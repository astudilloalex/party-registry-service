---

description: "Task list template for feature implementation"
---

# Tasks: [FEATURE NAME]

**Input**: Design documents from `/specs/[###-feature-name]/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**TDD**: Tests are mandatory. Generate small, ordered Red -> Green -> Refactor cycles for every new or changed production behavior.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story. Within a story, keep each behavior's Red, Green, and Refactor tasks adjacent.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Single project**: `src/`, `tests/` at repository root
- **Web app**: `backend/src/`, `frontend/src/`
- **Mobile**: `api/src/`, `ios/src/` or `android/src/`
- Paths shown below assume single project - adjust based on plan.md structure

<!--
  ============================================================================
  IMPORTANT: The tasks below are SAMPLE TASKS for illustration purposes only.

  The /speckit.tasks command MUST replace these with actual tasks based on:
  - User stories from spec.md (with their priorities P1, P2, P3...)
  - Feature requirements from plan.md
  - Entities from data-model.md
  - Endpoints from contracts/

   Tasks MUST be organized by user story so each story can be:
   - Implemented independently
   - Tested independently
   - Delivered as an MVP increment

   Within each story, tasks MUST be ordered as small behavior-level TDD cycles:
   - Red: add the lowest-layer test and run it to verify the expected failure
   - Green: add only enough production code to pass the targeted test
   - Refactor: improve the design while the relevant suite remains green

   Do not generate one batch of all tests followed by one batch of all implementation.

  DO NOT keep these sample tasks in the generated tasks.md file.
  ============================================================================
-->

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [ ] T001 Create project structure per implementation plan
- [ ] T002 Initialize [language] project with [framework] dependencies
- [ ] T003 [P] Configure linting and formatting tools

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

Examples of foundational tasks (adjust based on your project):

- [ ] T004 Setup database schema and migrations framework
- [ ] T005 [P] Implement authentication/authorization framework
- [ ] T006 [P] Setup API routing and middleware structure
- [ ] T007 Create base models/entities that all stories depend on
- [ ] T008 Configure error handling and logging infrastructure
- [ ] T009 Setup environment configuration management

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - [Title] (Priority: P1) 🎯 MVP

**Goal**: [Brief description of what this story delivers]

**Independent Test**: [How to verify this story works on its own]

### TDD Cycle 1: Core Behavior

> **NOTE: Run each Red task and verify the expected failure before its matching Green task.**

- [ ] T010 [US1] Red: Add failing unit test for [behavior] in tests/unit/test_[name].py
- [ ] T011 [US1] Green: Implement minimum [behavior] in src/models/[entity].py
- [ ] T012 [US1] Refactor: Improve [behavior] design and keep tests green in src/models/[entity].py

### TDD Cycle 2: Contract Behavior

- [ ] T013 [US1] Red: Add failing contract test for [endpoint] in tests/contract/test_[name].py
- [ ] T014 [US1] Green: Implement minimum [endpoint] behavior in src/[location]/[file].py
- [ ] T015 [US1] Refactor: Improve [endpoint] mapping and keep contract tests green in src/[location]/[file].py

### TDD Cycle 3: Integration Behavior

- [ ] T016 [US1] Red: Add failing integration test for [journey] in tests/integration/test_[name].py
- [ ] T017 [US1] Green: Implement minimum [journey] orchestration in src/services/[service].py
- [ ] T018 [US1] Refactor: Improve orchestration and keep integration tests green in src/services/[service].py

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: User Story 2 - [Title] (Priority: P2)

**Goal**: [Brief description of what this story delivers]

**Independent Test**: [How to verify this story works on its own]

### TDD Cycle 1: Core Behavior

- [ ] T019 [US2] Red: Add failing unit test for [behavior] in tests/unit/test_[name].py
- [ ] T020 [US2] Green: Implement minimum [behavior] in src/models/[entity].py
- [ ] T021 [US2] Refactor: Improve [behavior] design and keep tests green in src/models/[entity].py

### TDD Cycle 2: Boundary Behavior

- [ ] T022 [US2] Red: Add failing boundary test for [behavior] in tests/integration/test_[name].py
- [ ] T023 [US2] Green: Implement minimum boundary behavior in src/services/[service].py
- [ ] T024 [US2] Refactor: Improve boundary design and keep relevant tests green in src/services/[service].py

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently

---

## Phase 5: User Story 3 - [Title] (Priority: P3)

**Goal**: [Brief description of what this story delivers]

**Independent Test**: [How to verify this story works on its own]

### TDD Cycle 1: Core Behavior

- [ ] T025 [US3] Red: Add failing unit test for [behavior] in tests/unit/test_[name].py
- [ ] T026 [US3] Green: Implement minimum [behavior] in src/models/[entity].py
- [ ] T027 [US3] Refactor: Improve [behavior] design and keep tests green in src/models/[entity].py

### TDD Cycle 2: Boundary Behavior

- [ ] T028 [US3] Red: Add failing boundary test for [behavior] in tests/integration/test_[name].py
- [ ] T029 [US3] Green: Implement minimum boundary behavior in src/services/[service].py
- [ ] T030 [US3] Refactor: Improve boundary design and keep relevant tests green in src/services/[service].py

**Checkpoint**: All user stories should now be independently functional

---

[Add more user story phases as needed, following the same pattern]

---

## Phase N: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [ ] TXXX [P] Documentation updates in docs/
- [ ] TXXX Code cleanup and refactoring
- [ ] TXXX Performance optimization across all stories
- [ ] TXXX [P] Add missing cross-cutting regression tests in tests/unit/
- [ ] TXXX Security hardening
- [ ] TXXX Run quickstart.md validation

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
  - User stories can then proceed in parallel (if staffed)
  - Or sequentially in priority order (P1 → P2 → P3)
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **User Story 2 (P2)**: Can start after Foundational (Phase 2) - May integrate with US1 but should be independently testable
- **User Story 3 (P3)**: Can start after Foundational (Phase 2) - May integrate with US1/US2 but should be independently testable

### Within Each User Story

- Each behavior MUST be an ordered Red -> Green -> Refactor cycle
- The Red task MUST add the lowest-layer test and verify that it fails for the expected missing behavior
- The matching Green task MUST add only enough production code to pass the targeted test
- The Refactor task MUST keep the affected layer and architecture checks green
- No production behavior task may run before its matching Red task completes
- Lower-layer behavior cycles before dependent boundary cycles
- Contract and integration cycles remain adjacent to their matching implementation
- Story complete before moving to next priority

### Parallel Opportunities

- All Setup tasks marked [P] can run in parallel
- All Foundational tasks marked [P] can run in parallel (within Phase 2)
- Once Foundational phase completes, all user stories can start in parallel (if team capacity allows)
- Independent TDD cycles marked [P] can run in parallel when they do not modify the same files
- Models within a story marked [P] can run in parallel
- Different user stories can be worked on in parallel by different team members

---

## Parallel Example: User Story 1

```bash
# Launch independent TDD cycles only when they do not modify the same files:
Task: "Red -> Green -> Refactor cycle for [contract behavior]"
Task: "Red -> Green -> Refactor cycle for [independent integration behavior]"

# Launch all models for User Story 1 together:
Task: "Create [Entity1] model in src/models/[entity1].py"
Task: "Create [Entity2] model in src/models/[entity2].py"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL - blocks all stories)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Test User Story 1 independently
5. Deploy/demo if ready

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add User Story 1 → Test independently → Deploy/Demo (MVP!)
3. Add User Story 2 → Test independently → Deploy/Demo
4. Add User Story 3 → Test independently → Deploy/Demo
5. Each story adds value without breaking previous stories

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: User Story 1
   - Developer B: User Story 2
   - Developer C: User Story 3
3. Stories complete and integrate independently

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Record the expected Red failure, then the passing Green execution, for each behavior
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence
