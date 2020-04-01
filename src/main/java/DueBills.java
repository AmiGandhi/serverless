import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import com.amazonaws.services.simpleemail.model.*;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Calendar;
import java.util.UUID;


public class DueBills implements RequestHandler<SNSEvent, Object> {
    static LambdaLogger logger = null;
    static DynamoDB dynamoDB;
    static final String domain="dev.amigandhi.me";
    static final String FROM = "noreply@dev.amigandhi.me";
    static final String dynamodbTable = "csye6225-Dynamodb-table";

    @Override
    public Object handleRequest(SNSEvent request, Context context){

        String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(Calendar.getInstance().getTime());
        context.getLogger().log("Request received: " + timeStamp);
        context.getLogger().log(request.getRecords().get(0).getSNS().getMessage());
        context.getLogger().log("Domain : " + domain);
        final String TO = request.getRecords().get(0).getSNS().getMessage();

        try {

            context.getLogger().log("Connecting to dynamodb");
            initDynamoDB();
            context.getLogger().log("Connected to dynamodb");
            Table table = dynamoDB.getTable(dynamodbTable);
            long unixTime = Instant.now().getEpochSecond()+60*60;
            long now = Instant.now().getEpochSecond();
            context.getLogger().log("calculated time" + unixTime);
            context.getLogger().log( "current time" + now);
            if(table == null) {
                context.getLogger().log("Table not found");
            }
            else{
                Item item = table.getItem("Email", request.getRecords().get(0).getSNS().getMessage());
                if(item==null || (item!=null && Long.parseLong(item.get("ttlInMin").toString()) < now)) {
                    String token = UUID.randomUUID().toString();
                    Item itemPut = new Item()
                            .withPrimaryKey("Email", request.getRecords().get(0).getSNS().getMessage())
                            .withString("token", token)
                            .withNumber("ttlInMin", unixTime);

                    context.getLogger().log("AWS request ID:" + context.getAwsRequestId());

                    table.putItem(itemPut);

                    context.getLogger().log("AWS message ID:" + request.getRecords().get(0).getSNS().getMessageId());

                    invokeSES(FROM, TO, token);

                } else {
                        context.getLogger().log(item.toJSON() + "Email Already sent!");
                }
            }
        } catch (Exception ex) {
            context.getLogger().log ("The email was not sent. Error message: "
                    + ex.getMessage());
        }


        timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(Calendar.getInstance().getTime());
        context.getLogger().log("Invocation completed: " + timeStamp);
        return null;
    }

    private static void initDynamoDB() throws Exception {
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard()
                .withRegion(Regions.US_EAST_1)
                .build();
        dynamoDB = new DynamoDB(client);
    }

    private static void invokeSES(String FROM, String TO, String token) throws Exception {
        AmazonSimpleEmailService client =
                AmazonSimpleEmailServiceClientBuilder.standard()
                        .withRegion(Regions.US_EAST_1)
                        .build();

            SendEmailRequest req = new SendEmailRequest()
                    .withDestination(
                            new Destination()
                                    .withToAddresses(TO))
                    .withMessage(
                            new Message()
                                    .withBody(
                                            new Body()
                                                    .withHtml(
                                                            new Content()
                                                                    .withCharset(
                                                                            "UTF-8")
                                                                    .withData(
                                                                            "Please click on the below link to reset the password<br/>"+
                                                                                    "<p><a href='#'>http://"+domain+"/reset?email="+TO+"&token="+token+"</a></p>"))
                                    )
                                    .withSubject(
                                            new Content().withCharset("UTF-8")
                                                    .withData("Password Reset Link")))
                    .withSource(FROM);
            SendEmailResult response = client.sendEmail(req);
            System.out.println("Email sent!");
        }
    }
}