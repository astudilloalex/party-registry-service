package com.alexastudillo.partyregistry.application.usecase;

import com.alexastudillo.partyregistry.application.error.ApplicationException;
import com.alexastudillo.partyregistry.application.error.ApplicationFailure;
import com.alexastudillo.partyregistry.application.model.RequestMetadata;
import com.alexastudillo.partyregistry.application.port.CountryReferencePort;
import io.smallrye.mutiny.Uni;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Coordinates country-reference checks shared by natural-person write use
 * cases.
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
}
