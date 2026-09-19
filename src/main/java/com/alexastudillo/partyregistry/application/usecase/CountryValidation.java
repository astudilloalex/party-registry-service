package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.CountryReferencePort;
import com.alexastudillo.partyregistry.domain.normalization.PartyTextNormalization;
import io.smallrye.mutiny.Uni;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Coordinates country-reference checks shared by Party write use cases.
 */
final class CountryValidation {

    private CountryValidation() {
    }

    static Uni<Void> validateChangedCountry(
            CountryReferencePort countryReferencePort,
            RequestMetadata requestMetadata,
            @Nullable String currentCode,
            @Nullable String resultingCode) {
        if (resultingCode == null || Objects.equals(currentCode, resultingCode)) {
            return Uni.createFrom().voidItem();
        }

        return countryReferencePort.isRecognizedCountry(requestMetadata, resultingCode)
                .onItem().transformToUni(recognized -> Boolean.TRUE.equals(recognized)
                        ? Uni.createFrom().voidItem()
                        : Uni.createFrom().failure(new ApplicationException(
                                new ApplicationFailure.UnrecognizedBirthCountry(resultingCode))));
    }

    static Uni<Void> validateIncorporationCountry(
            CountryReferencePort countryReferencePort,
            RequestMetadata requestMetadata,
            String countryCode) {
        return countryReferencePort.isRecognizedCountry(requestMetadata, countryCode)
                .onItem().transformToUni(recognized -> Boolean.TRUE.equals(recognized)
                        ? Uni.createFrom().voidItem()
                        : Uni.createFrom().failure(new ApplicationException(
                                new ApplicationFailure.UnrecognizedIncorporationCountry(countryCode))));
    }

    static Uni<Void> validateChangedIncorporationCountry(
            CountryReferencePort countryReferencePort,
            RequestMetadata requestMetadata,
            String currentCode,
            String resultingCode) {
        String canonicalCode = PartyTextNormalization.countryCode(resultingCode);
        if (Objects.equals(PartyTextNormalization.countryCode(currentCode), canonicalCode)) {
            return Uni.createFrom().voidItem();
        }
        return countryReferencePort.isRecognizedCountry(requestMetadata, canonicalCode)
                .flatMap(recognized -> {
                    if (Boolean.TRUE.equals(recognized)) {
                        return Uni.createFrom().voidItem();
                    }
                    ApplicationFailure failure = Boolean.FALSE.equals(recognized)
                            ? new ApplicationFailure.UnrecognizedIncorporationCountry(canonicalCode)
                            : new ApplicationFailure.DependencyUnavailable("geographic-reference");
                    return Uni.createFrom().failure(new ApplicationException(failure));
                });
    }
}
