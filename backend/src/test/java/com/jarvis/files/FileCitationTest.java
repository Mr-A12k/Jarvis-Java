package com.jarvis.files;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class FileCitationTest {
    private final List<LocalFiles.Hit> hits=List.of(new LocalFiles.Hit("doc.pdf:1","Access code: COPPER-936."));
    @Test void missingOrInvalidCitationsUseExcerptsRatherThanInventedClaims() {
        for(String answer:List.of("This grants administrator access.","Administrator access [2]","Administrator access [0]","Administrator access [999999999999999999]")) {
            assertThat(FileController.checkedAnswer(answer,hits)).contains("COPPER-936","[1]").doesNotContain("Administrator","grants");
        }
    }
    @Test void preservesValidCitationAndExplicitInsufficientEvidence() {
        assertThat(FileController.checkedAnswer("COPPER-936 [1]",hits)).isEqualTo("COPPER-936 [1]");
        assertThat(FileController.checkedAnswer("The supplied excerpts do not answer this question.",hits)).isEqualTo("The supplied excerpts do not answer this question.");
    }
}
