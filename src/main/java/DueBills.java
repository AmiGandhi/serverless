import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.PutItemSpec;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import com.amazonaws.services.simpleemail.model.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Calendar;
import java.util.UUID;

public class DueBills implements RequestHandler<SNSEvent, Object> {
    static LambdaLogger logger = null;
    static DynamoDB dynamoDB;
    String domain = System.getenv("domain");
    final String FROM = "no-reply@" + domain;
    final String dynamodbTable = System.getenv("dynamoDBTable");

    @Override
    public Object handleRequest(SNSEvent request, Context context){

        String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(Calendar.getInstance().getTime());
        context.getLogger().log("Request received: " + timeStamp + "from domain: " + domain);
        context.getLogger().log(request.getRecords().get(0).getSNS().getMessage());

        // unix time
        long now = Calendar.getInstance().getTimeInMillis()/1000;

        //setting TTL to 60 minutes
        long TTL = 60 * 60;
        long totalTTL = TTL + now ;

        String billPayload = request.getRecords().get(0).getSNS().getMessage();
        JsonObject jsonObject = new JsonParser().parse(billPayload).getAsJsonObject();

        String emailVal = "";
        context.getLogger().log("Extracting recipient email id to send emails");
        String TO = jsonObject.get("Email").getAsString();

        JsonArray listBillId =jsonObject.getAsJsonArray("dueBillIds");
        context.getLogger().log("iterating over bill list json pay");
        for(int i = 0; i<listBillId.size();i++){
            String temp = listBillId.get(i).toString();
            emailVal += "<p><a href='#'>http://" + domain + "/v1/bill/"+ temp +"</a></p><br>";
            emailVal =  emailVal.replaceAll("\"","");
            context.getLogger().log(emailVal);
        }

        context.getLogger().log(emailVal);

        try {

            context.getLogger().log("Connecting to dynamodb");
            initDynamoDB();
            context.getLogger().log("Connected to dynamodb");
            Table table = dynamoDB.getTable(dynamodbTable);

            if(table == null) {
                context.getLogger().log("Table not found");
            }
            else{
                long ttlDBValue = 0;
                Item item = table.getItem("Email", TO);

                if (item != null) {
                    context.getLogger().log("Checking for timestamp");
                    ttlDBValue = item.getLong("ttl");
                }

                if(item == null || (ttlDBValue < now && ttlDBValue != 0)) {
                    String token = UUID.randomUUID().toString();
                    context.getLogger().log("Checking for valid ttl");
                    context.getLogger().log("ttl expired, creating new token and sending email");
                    table
                        .putItem(
                                new PutItemSpec().withItem(new Item()
                                        .withPrimaryKey("Email", TO)
                                        .withString("token", token)
                                        .withLong("ttl", totalTTL)));

                    context.getLogger().log("AWS request ID:" + context.getAwsRequestId());
                    context.getLogger().log("AWS message ID:" + request.getRecords().get(0).getSNS().getMessageId());

                    invokeSES(context, FROM, TO, token, emailVal);

                } else {
                        context.getLogger().log(item.toJSON() + "Email Already sent!");
                }
            }
        } catch (Exception ex) {
            context.getLogger().log ("The email was not sent. Error message: "
                    + ex.getMessage());
        }

        String endTimeStamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(Calendar.getInstance().getTime());
        context.getLogger().log("Invocation completed: " + endTimeStamp);
        return null;

    }

    private static void initDynamoDB() throws Exception {
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard()
                .withRegion(Regions.US_EAST_1)
                .build();
        dynamoDB = new DynamoDB(client);
    }

    private static void invokeSES(Context context, String FROM, String TO, String token, String emailVal) throws IOException {

        try {

            AmazonSimpleEmailService client =
                    AmazonSimpleEmailServiceClientBuilder.standard()
                            .withRegion(Regions.US_EAST_1)
                            .build();

            context.getLogger().log("Connected to Amazon SES!");

            SendEmailRequest req = new SendEmailRequest()
                    .withDestination(new Destination()
                            .withToAddresses(TO))
                    .withMessage(new Message()
                            .withBody(new Body()
                                    .withHtml(new Content()
                                            .withCharset("UTF-8")
                                            .withData("Please find below all due bill items<br/>" + emailVal)))
                            .withSubject(new Content()
                                    .withCharset("UTF-8")
                                    .withData("List Of Due Bills")))
                    .withSource(FROM);

            SendEmailResult response = client.sendEmail(req);
            context.getLogger().log("Email sent with response: " + response);
        } catch (Exception ex) {
            System.out.println("The email was not sent. Error message: "
                    + ex.getMessage());
        }
    }
    }