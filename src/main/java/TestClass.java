import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

import java.util.Map;

public class TestClass implements RequestHandler<Map<String,Object>, String>{
    static LambdaLogger logger = null;

    @Override
    public String handleRequest(Map<String,Object> input, Context context) {
        TestClass.logger = context.getLogger();
        logger.log("Lambda Function ran successfully");

        System.out.println(input);
        return "Hello";

    }
}