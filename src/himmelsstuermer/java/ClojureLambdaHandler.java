package himmelsstuermer.java;

// Import necessary Clojure and Java classes
import clojure.java.api.Clojure;
import clojure.lang.IFn;
import com.amazonaws.services.lambda.runtime.Context;
import java.util.List;
import java.util.Map;

public class ClojureLambdaHandler {
    static {
        try {
            System.out.println("Initializing Clojure environment...");

            // Get the 'require' function from 'clojure.core'
            IFn require = Clojure.var("clojure.core", "require");
            System.out.println("Require function obtained: " + require);

            // Read the namespace symbol
            Object namespaceSymbol = Clojure.read("himmelsstuermer.core");
            System.out.println("Namespace symbol obtained: " + namespaceSymbol);

            // Invoke 'require' on the namespace
            require.invoke(namespaceSymbol);
            System.out.println("Namespace required successfully");
        } catch (Exception e) {
            System.out.println("Exception during static initialization: " + e);
            e.printStackTrace();
        }
    }

    // Handle SQS event payloads and include context
    public static void handleRequest(Map<String, Object> event, Context context) {
        System.out.println("Lambda invoked with context: " + context.getAwsRequestId());

        // Extract the "Records" array from the event
        List<Map<String, Object>> records = (List<Map<String, Object>>) event.get("Records");

        // Check if records are present
        if (records != null) {
            // Pass the message and context information to your Clojure function for further processing
            IFn myFunction = Clojure.var("himmelsstuermer.core", "-main");  // Replace with your function name
            myFunction.invoke(records, context);  // Invoke the Clojure function with the message body and context
        } else {
            System.out.println("No records to process.");
        }
    }
}
