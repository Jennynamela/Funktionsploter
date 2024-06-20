package functionplotter;

import main.java.clerk.Clerk;
import main.java.clerk.LiveView;

public class FunctionPlotterApp implements Clerk {
    private final LiveView view;

    public FunctionPlotterApp() {
        this.view = Clerk.view();
    }

    public void start() {
        Clerk.load(view, "src//main//resources//web//index.html");
        setupRoutes();
    }

    private void setupRoutes() {
        view.createResponseContext("/plot", response -> { 
            // Handle plot request
            System.out.println("Plot request received: " + response);
            // Implement your logic to handle the plot request here
        });
    }

    public static void main(String[] args) {
        FunctionPlotterApp app = new FunctionPlotterApp();
        app.start();
    }
}
