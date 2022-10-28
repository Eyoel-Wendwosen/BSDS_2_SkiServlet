import com.google.gson.Gson;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;

import javax.servlet.*;
import javax.servlet.annotation.*;
import javax.servlet.http.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@WebServlet(name = "SkierServlet", value = "/SkierServlet")
public class SkierServlet extends HttpServlet {

    private static final String QUEUE_NAME = "ski_event";

    private ConnectionFactory factory;
    private RMQChannelPool rmqChannelPool;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        // Do some stuff with initial parameters
        System.out.println("Here");

        factory = new ConnectionFactory();
        // TODO: change this to remote rabbitmq site
//        factory.setHost("localhost");
        factory.setHost("ec2-44-227-151-235.us-west-2.compute.amazonaws.com");
        factory.setUsername("test");
        factory.setPassword("test");

        Connection connection = null;
        try {
            connection = factory.newConnection();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }

        RMQChannelFactory rmqChannelFactory = new RMQChannelFactory(connection);

        rmqChannelPool = new RMQChannelPool(64, rmqChannelFactory);
        System.out.printf("Created %d channels.\n", rmqChannelFactory.getChannelCount());
//        channel = connection.createChannel();
    }


    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        String urlPath = request.getPathInfo();
        // check we have a URL!
        if (urlPath == null || urlPath.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().write("missing parameters");
            return;
        }

        String[] urlParts = urlPath.split("/");
        // and now validate url path and return the response status code
        // (and maybe also some value if input is valid)

        if (!isUrlValid(urlParts)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        } else {
            response.setStatus(HttpServletResponse.SC_OK);
            // do any sophisticated processing with urlParts which contains all the url params
            // TODO: process url params in `urlParts`
            response.getWriter().write("It works!");
        }
    }

    private boolean isUrlValid(String[] urlParts) {
        int resortId = Integer.parseInt(urlParts[0]);
        int seasonId = Integer.parseInt(urlParts[1]);
        int days = Integer.parseInt(urlParts[2]);
        int skiersId = Integer.parseInt(urlParts[3]);

        if (resortId < 1 || resortId > 10 ||
                seasonId != 2022 ||
                days != 1 ||
                skiersId < 1 || skiersId > 100000) {
            return false;
        }
        return true;
    }

    private boolean isUrlValid(SkiEvent skiEvent, String urlParam) {
        int liftId = skiEvent.getLiftId();
        int time = skiEvent.getTime();

        Pattern pattern = Pattern.compile("(\\d+)");
        Matcher matcher = pattern.matcher(urlParam);
        List<Integer> params = new ArrayList<>();

        while (matcher.find()) {
            params.add(Integer.parseInt(matcher.group(0)));
        }

        if (params.size() != 4) {
            System.out.println("Here");
            return false;
        }

        int resortId = params.get(0);
        int seasonId = params.get(1);
        int days = params.get(2);
        int skiersId = params.get(3);

        if (liftId < 1 || liftId > 40 ||
                resortId < 1 || resortId > 10 ||
                seasonId != 2022 ||
                days != 1 ||
                skiersId < 1 || skiersId > 100000 ||
                time < 1 || time > 360) {
            System.out.println("Here2");
            return false;
        }

        skiEvent.setResortId(resortId);
        skiEvent.setSeasonId(seasonId);
        skiEvent.setDayId(days);
        skiEvent.setSkierId(skiersId);
        return true;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");

        String urlPath = request.getPathInfo();

        if (urlPath == null || urlPath.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().write("Some parameters are missing");
            return;
        }

        StringBuilder data = new StringBuilder();
        BufferedReader reader = request.getReader();
        String line;
        while ((line = reader.readLine()) != null) {
            data.append(line);
        }

        Gson gson = new Gson();
        SkiEvent skiEvent = gson.fromJson(data.toString(), SkiEvent.class);

        if (!isUrlValid(skiEvent, urlPath)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().write("Some Error Occurred");
        } else {
            String responseData = gson.toJson(skiEvent);
            Channel channel = rmqChannelPool.borrowObject();
            channel.basicPublish("", QUEUE_NAME, MessageProperties.BASIC, responseData.getBytes());
            rmqChannelPool.returnObject(channel);
            response.setStatus(HttpServletResponse.SC_CREATED);
            response.getWriter().write(responseData);
        }

    }

}
