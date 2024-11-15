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

            Object coreNamespaceSymbol = Clojure.read("himmelsstuermer.core");
            System.out.println("Core namespace symbol obtained: " + coreNamespaceSymbol);

            require.invoke(coreNamespaceSymbol);
            System.out.println("Core namespace required successfully");
            
            Object storageNamespaceSymbol = Clojure.read("himmelsstuermer.core.storage");
            System.out.println("Storage namespace symbol obtained: " + storageNamespaceSymbol);

            require.invoke(storageNamespaceSymbol);
            System.out.println("Storage namespace required successfully");

            IFn setStorage = Clojure.var("himmelsstuermer.core.storage", "set-storage");
            setStorage.invoke();
            System.out.println("Storage setup function invoked successfully");

            Object stateNamespaceSymbol = Clojure.read("himmelsstuermer.core.state");
            System.out.println("State namespace symbol obtained: " + stateNamespaceSymbol);

            require.invoke(stateNamespaceSymbol);
            System.out.println("State namespace required successfully");

            IFn createState = Clojure.var("himmelsstuermer.core.state", "create-initial-state");
            createState.invoke();
            System.out.println("Initial state setup function invoked successfully");
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
