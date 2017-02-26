import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import org.jetbrains.annotations.NotNull;
import ruby.codeInsight.types.signature.RSignature;
import ruby.codeInsight.types.storage.server.RSignatureStorageServer;
import ruby.codeInsight.types.storage.server.RSignatureStorageServerImpl;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;

public class LambdaFunctionHandler implements RequestHandler<S3Event, Object> {
    @Override
    public Object handleRequest(@NotNull final S3Event event, @NotNull final Context context) {
        final LambdaLogger logger = context.getLogger();
        logger.log("Input: " + event);
        final String statFileName = event.getRecords().get(0).getS3().getObject().getKey();
        try {
            final Properties properties = getProperties();
            final RSignatureStorageServer server = new RSignatureStorageServerImpl(
                    properties.getProperty("host"), properties.getProperty("port"),
                    properties.getProperty("login"), properties.getProperty("password"),
                    properties.getProperty("dbName"));
            final List<RSignature> signatures = server.getSignaturesFromStatFile(statFileName, false);
            server.insertSignaturesToStorage(signatures);
            server.deleteStatFile(statFileName, false);
        } catch (ClassNotFoundException | IOException | SQLException e) {
            logger.log(e.toString());
            return "FAIL";
        }

        return "OK";
    }

    @NotNull
    private static Properties getProperties() throws IOException {
        final String PROPERTIES_FILE = "aws.properties";
        final Properties properties = new Properties();
        final URL propertiesURL = LambdaFunctionHandler.class.getClassLoader().getResource(PROPERTIES_FILE);
        if (propertiesURL == null) {
            throw new FileNotFoundException("File " + PROPERTIES_FILE + " not found");
        }

        final String propertiesPath = propertiesURL.getPath();
        properties.load(new FileInputStream(propertiesPath));
        return properties;
    }
}