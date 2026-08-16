package com.alexastudillo.partyregistry.application.port;

import com.alexastudillo.partyregistry.application.DecryptionSecurityLogEvent;
import java.util.concurrent.CompletionStage;

public interface DecryptionSecurityLogPort {
    CompletionStage<Void> emit(DecryptionSecurityLogEvent event);
}
