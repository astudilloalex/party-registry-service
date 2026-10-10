"""Verify secret rendering and deployment recovery without contacting a VPS."""

import base64
import hashlib
import json
import os
from pathlib import Path
import stat
import subprocess
import sys
import tempfile
import unittest


DEPLOY_DIRECTORY = Path(__file__).resolve().parents[1]
TEMPLATE = DEPLOY_DIRECTORY / "quadlet/party-registry-service.env.example"
RENDERER = DEPLOY_DIRECTORY / "vps/render-env.py"
DEPLOY_SCRIPT = DEPLOY_DIRECTORY / "vps/deploy.sh"
IMAGE = "ghcr.io/astudilloalex/party-registry-service:sha-" + "a" * 40


def production_secrets() -> dict[str, str]:
    """Provide independent test-only secrets; never read production credentials."""
    return {
        "DB_PASSWORD": "database-test-password",
        "FLYWAY_DB_PASSWORD": "migration-test-password",
        "RABBITMQ_USERNAME": "party_registry_service_prod",
        "RABBITMQ_PASSWORD": "rabbitmq-test-password",
        "PARTY_IDENTIFIER_ENCRYPTION_KEY_V1": base64.b64encode(bytes(range(32))).decode(),
        "PARTY_IDENTIFIER_INDEX_HMAC_KEY": base64.b64encode(bytes(range(32, 64))).decode(),
        "PARTY_REGISTRATION_IDEMPOTENCY_HMAC_KEY": base64.b64encode(bytes(range(64, 96))).decode(),
        "PARTY_CURSOR_SIGNING_KEY_V1": base64.b64encode(bytes(range(96, 128))).decode(),
    }


def render(output: Path, values: dict[str, str]) -> subprocess.CompletedProcess[str]:
    """Run the real renderer with an isolated environment and capture its output."""
    return subprocess.run(
        [sys.executable, str(RENDERER), str(TEMPLATE), str(output)],
        env={"PATH": os.environ["PATH"], **values},
        text=True,
        capture_output=True,
        check=False,
    )


class EnvironmentRenderingTest(unittest.TestCase):
    """Check literal secret preservation, validation, and private file creation."""

    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)

    def test_literal_password_and_configuration_override(self) -> None:
        values = production_secrets()
        values["DB_PASSWORD"] = " leading $() `command` # & | \\ ' \" = trailing "
        values["DB_USERNAME"] = "custom_runtime"
        values["RABBITMQ_VIRTUAL_HOST"] = "custom-vhost"
        output = self.directory / "production.env"
        result = render(output, values)
        self.assertEqual(result.returncode, 0, result.stderr)
        content = output.read_text()
        self.assertIn(f"DB_PASSWORD={values['DB_PASSWORD']}\n", content)
        self.assertIn("DB_USERNAME=custom_runtime\n", content)
        self.assertIn("RABBITMQ_VIRTUAL_HOST=custom-vhost\n", content)
        self.assertNotIn("REPLACE_WITH_", content)
        self.assertEqual(stat.S_IMODE(output.stat().st_mode), 0o600)
        self.assertNotIn(values["DB_PASSWORD"], result.stdout + result.stderr)

    def test_each_missing_secret_fails_before_creating_output(self) -> None:
        for name in production_secrets():
            with self.subTest(secret=name):
                values = production_secrets()
                del values[name]
                output = self.directory / name
                result = render(output, values)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn(name, result.stderr)
                self.assertFalse(output.exists())

    def test_invalid_values_are_rejected_without_disclosing_them(self) -> None:
        invalid_values = (
            ("DB_PASSWORD", "REPLACE_WITH_DATABASE_PASSWORD"),
            ("DB_PASSWORD", "password\nRABBITMQ_HOST=attacker"),
            ("DB_PASSWORD", "password\rnext-line"),
            ("PARTY_IDENTIFIER_ENCRYPTION_KEY_V1", "invalid!base64"),
            ("PARTY_IDENTIFIER_ENCRYPTION_KEY_V1", base64.b64encode(b"x" * 31).decode()),
            ("RABBITMQ_HOST", "http://rabbitmq"),
            ("RABBITMQ_PORT", "65536"),
            ("PARTY_OUTBOX_MODE", "invalid-mode"),
            ("PARTY_IDENTIFIER_CURRENT_ENCRYPTION_KEY_VERSION", "2"),
            ("PARTY_CURSOR_CURRENT_SIGNING_KEY_ID", "v2"),
        )
        for index, (name, value) in enumerate(invalid_values):
            with self.subTest(variable=name, case=index):
                values = production_secrets()
                values[name] = value
                output = self.directory / str(index)
                result = render(output, values)
                self.assertNotEqual(result.returncode, 0)
                self.assertFalse(output.exists())
                self.assertNotIn(value, result.stdout + result.stderr)

    def test_duplicate_cryptographic_keys_are_rejected(self) -> None:
        values = production_secrets()
        values["PARTY_IDENTIFIER_INDEX_HMAC_KEY"] = values["PARTY_IDENTIFIER_ENCRYPTION_KEY_V1"]
        output = self.directory / "production.env"
        result = render(output, values)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("independent", result.stderr)
        self.assertFalse(output.exists())

    def test_existing_output_and_symlink_are_not_overwritten(self) -> None:
        existing = self.directory / "existing.env"
        existing.write_text("existing production configuration")
        for output in (existing, self.directory / "link.env"):
            with self.subTest(path=output.name):
                if output != existing:
                    output.symlink_to(existing)
                result = render(output, production_secrets())
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(existing.read_text(), "existing production configuration")


MOCK_COMMAND = r'''#!/usr/bin/env python3
"""Simulate external services while keeping filesystem mutations real."""
import hashlib
import json
import os
from pathlib import Path
import signal
import subprocess
import sys

name = Path(sys.argv[0]).name
arguments = sys.argv[1:]
state_file = Path(os.environ["MOCK_STATE"])
state = json.loads(state_file.read_text()) if state_file.exists() else {"commands": [], "restarts": []}
state["commands"].append([name, *arguments])
mode = os.environ.get("MOCK_MODE", "success")
status = 0

if name == "uname":
    print("x86_64")
elif name == "podman":
    if arguments[0] == "pull":
        installed = Path(os.environ["DEPLOYMENT_ENVIRONMENT_FILE"])
        state["environment_at_pull"] = hashlib.sha256(installed.read_bytes()).hexdigest() if installed.exists() else None
        status = int(mode == "pull-failure")
elif name == "systemctl":
    if "daemon-reload" in arguments:
        state["reloads"] = state.get("reloads", 0) + 1
        status = int(mode == "reload-failure" and state["reloads"] == 1)
    elif "cat" in arguments:
        status = int(mode == "generator-failure")
    elif "restart" in arguments:
        installed = Path(os.environ["DEPLOYMENT_ENVIRONMENT_FILE"])
        state["restarts"].append(hashlib.sha256(installed.read_bytes()).hexdigest() if installed.exists() else None)
        status = int(mode == "rollback-failure" or (mode == "startup-failure" and len(state["restarts"]) == 1))
        if mode == "termination" and len(state["restarts"]) == 1:
            os.kill(os.getppid(), signal.SIGTERM)
elif name == "curl":
    status = int(mode == "health-failure")
elif name == "mv":
    if mode == "install-failure" and arguments[-1].endswith(".container") and not state.get("install_failed"):
        state["install_failed"] = True
        status = 1
    else:
        status = subprocess.run(["/bin/mv", *arguments], check=False).returncode

state_file.write_text(json.dumps(state))
sys.exit(status)
'''


class DeploymentRecoveryTest(unittest.TestCase):
    """Run the real shell script against isolated files and simulated services."""

    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        self.stage = self.directory / "stage"
        self.stage.mkdir()
        self.incoming = self.stage / "party-registry-service.env"
        result = render(self.incoming, production_secrets())
        self.assertEqual(result.returncode, 0, result.stderr)
        self.environment = self.directory / "production/env/party-registry-service.env"
        self.config_home = self.directory / "config"
        self.quadlet = self.config_home / "containers/systemd/party-registry-service.container"
        (self.stage / "party-registry-service.container.template").write_bytes(
            (DEPLOY_DIRECTORY / "quadlet/party-registry-service.container.template").read_bytes()
        )
        self.mock_bin = self.directory / "bin"
        self.mock_bin.mkdir()
        for name in ("podman", "systemctl", "journalctl", "curl", "sleep", "mv", "uname"):
            command = self.mock_bin / name
            command.write_text(MOCK_COMMAND)
            command.chmod(0o755)
        self.backups = self.directory / "temporary"
        self.backups.mkdir()
        self.state_file = self.directory / "state.json"

    def provision_previous_deployment(self) -> None:
        """Preserve distinct old credentials and an old image for recovery assertions."""
        self.environment.parent.mkdir(parents=True)
        self.previous_environment = self.incoming.read_bytes().replace(
            b"database-test-password", b"previous-database-password"
        )
        self.environment.write_bytes(self.previous_environment)
        self.environment.chmod(0o600)
        self.quadlet.parent.mkdir(parents=True)
        self.previous_quadlet = b"[Container]\nImage=previous-image\n"
        self.quadlet.write_bytes(self.previous_quadlet)

    def deploy(self, mode: str = "success", incoming: bool = True) -> subprocess.CompletedProcess[str]:
        """Exercise deployment without real Podman, systemd, SSH, or network access."""
        arguments = ["bash", str(DEPLOY_SCRIPT), IMAGE, str(self.stage)]
        if incoming:
            arguments.append(str(self.incoming))
        return subprocess.run(
            arguments,
            env={
                "PATH": f"{self.mock_bin}:/usr/bin:/bin",
                "HOME": str(self.directory),
                "XDG_CONFIG_HOME": str(self.config_home),
                "DEPLOYMENT_ENVIRONMENT_FILE": str(self.environment),
                "TMPDIR": str(self.backups),
                "MOCK_STATE": str(self.state_file),
                "MOCK_MODE": mode,
            },
            text=True,
            capture_output=True,
            timeout=30,
            check=False,
        )

    def test_first_deployment_installs_private_environment_and_correct_quadlet(self) -> None:
        result = self.deploy()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.environment.read_bytes(), self.incoming.read_bytes())
        self.assertIn("RABBITMQ_VIRTUAL_HOST=platform\n", self.environment.read_text())
        self.assertEqual(stat.S_IMODE(self.environment.stat().st_mode), 0o600)
        self.assertEqual(stat.S_IMODE(self.quadlet.stat().st_mode), 0o600)
        content = self.quadlet.read_text()
        self.assertIn(f"Image={IMAGE}\n", content)
        self.assertIn(f"EnvironmentFile={self.environment}\n", content)
        self.assertNotIn("@@", content)
        self.assertFalse(list(self.backups.iterdir()))
        for value in production_secrets().values():
            self.assertNotIn(value, result.stdout + result.stderr)

    def test_success_replaces_existing_configuration_after_image_pull(self) -> None:
        self.provision_previous_deployment()
        result = self.deploy()
        self.assertEqual(result.returncode, 0, result.stderr)
        state = json.loads(self.state_file.read_text())
        self.assertEqual(state["environment_at_pull"], hashlib.sha256(self.previous_environment).hexdigest())
        self.assertEqual(self.environment.read_bytes(), self.incoming.read_bytes())
        self.assertEqual(len(state["restarts"]), 1)
        self.assertFalse(list(self.backups.iterdir()))

    def test_failures_restore_both_files_and_restart_with_previous_credentials(self) -> None:
        self.provision_previous_deployment()
        for mode in ("startup-failure", "generator-failure", "reload-failure", "install-failure", "health-failure", "termination"):
            with self.subTest(failure=mode):
                self.state_file.unlink(missing_ok=True)
                result = self.deploy(mode)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(self.environment.read_bytes(), self.previous_environment)
                self.assertEqual(self.quadlet.read_bytes(), self.previous_quadlet)
                state = json.loads(self.state_file.read_text())
                self.assertEqual(state["restarts"][-1], hashlib.sha256(self.previous_environment).hexdigest())
                self.assertFalse(list(self.backups.iterdir()))

    def test_pull_failure_leaves_previous_service_configuration_untouched(self) -> None:
        self.provision_previous_deployment()
        result = self.deploy("pull-failure")
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.environment.read_bytes(), self.previous_environment)
        self.assertEqual(self.quadlet.read_bytes(), self.previous_quadlet)
        self.assertFalse(json.loads(self.state_file.read_text())["restarts"])

    def test_first_deployment_failure_removes_new_environment_and_quadlet(self) -> None:
        result = self.deploy("startup-failure")
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(self.environment.exists())
        self.assertFalse(self.quadlet.exists())
        self.assertFalse(list(self.backups.iterdir()))

    def test_placeholder_and_duplicate_values_fail_before_service_changes(self) -> None:
        self.provision_previous_deployment()
        original = self.incoming.read_text()
        for content in (
            original.replace("database-test-password", "REPLACE_WITH_PASSWORD"),
            original + "DB_PASSWORD=duplicate-password\n",
            original + "DB_PASSWORD=\n",
        ):
            with self.subTest(duplicate="duplicate-password" in content):
                self.incoming.write_text(content)
                result = self.deploy()
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(self.environment.read_bytes(), self.previous_environment)
                self.assertEqual(self.quadlet.read_bytes(), self.previous_quadlet)
                state = json.loads(self.state_file.read_text())
                self.assertFalse(state["restarts"])
                self.assertTrue(all(command[0] == "uname" for command in state["commands"]))

    def test_manual_deployment_reuses_existing_environment(self) -> None:
        self.provision_previous_deployment()
        result = self.deploy(incoming=False)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.environment.read_bytes(), self.previous_environment)

    def test_failed_recovery_retains_private_backups(self) -> None:
        self.provision_previous_deployment()
        result = self.deploy("rollback-failure")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Rollback was incomplete", result.stderr)
        directories = list(self.backups.iterdir())
        self.assertEqual(len(directories), 1)
        backup = directories[0]
        self.assertEqual(stat.S_IMODE(backup.stat().st_mode), 0o700)
        self.assertEqual((backup / "previous.env").read_bytes(), self.previous_environment)
        self.assertEqual(stat.S_IMODE((backup / "previous.env").stat().st_mode), 0o600)


if __name__ == "__main__":
    unittest.main()
