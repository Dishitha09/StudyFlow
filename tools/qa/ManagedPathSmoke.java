import com.studyflow.config.StudyFlowContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Use only a disposable profile with materials/files redirected to a second empty test directory. */
class ManagedPathSmoke {
    public static void main(String[] args) throws Exception {
        Path profile = Path.of(args[0]);
        Path redirectedTarget = Path.of(args[1]);
        Path source = Files.createTempFile("studyflow-managed-path-", ".pdf");
        Files.writeString(source, "%PDF test fixture for checking the path before parsing");
        try (StudyFlowContext context = StudyFlowContext.open(profile)) {
            try {
                context.materialLibrary().upload(source, "Fixture only", "", null);
                throw new AssertionError("Redirected material folder was accepted.");
            } catch (IOException refused) {
                if (!refused.getMessage().contains("redirected library folder")) throw refused;
            }
            try (var paths = Files.list(redirectedTarget)) {
                if (paths.findAny().isPresent()) throw new AssertionError("Test target received unintended files.");
            }
            if (!context.materialLibrary().findAll().isEmpty()) throw new AssertionError("Unexpected PDF record.");
        } finally {
            Files.deleteIfExists(source);
        }
        System.out.println("The redirected PDF directory was blocked before writes.");
    }
}
