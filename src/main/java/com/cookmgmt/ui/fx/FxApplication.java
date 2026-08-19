package com.cookmgmt.ui.fx;

import com.cookmgmt.app.AppContext;
import com.cookmgmt.notify.InMemoryNotifier;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.Objects;

/**
 * The JavaFX front end.
 *
 * <p>Three tabs - Customer, Chef, Admin - over the same {@link AppContext} the console interface
 * uses. The rule that makes this worth having as evidence of the restructuring: <b>no class in this
 * package contains a business rule</b>. Conflict detection, substitution choice, pricing, stock
 * reservation and chef assignment are all service calls. If a controller here started deciding
 * whether a meal was vegan, the two interfaces would immediately be able to disagree.
 *
 * <p>Run with {@code ./mvnw javafx:run}.
 */
public class FxApplication extends Application {

    private final AppContext app = AppContext.withSampleData(new InMemoryNotifier());

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // The views are held and registered rather than built inline and discarded. Because they
        // all read the same services, an action on one tab changes what the others should show;
        // the refresher is what lets a cancellation on the Customer tab reach the chef's queue and
        // the admin report without any tab knowing the others exist.
        ViewRefresher refresher = new ViewRefresher();
        CustomerView customerView = new CustomerView(app, refresher);
        ChefView chefView = new ChefView(app, refresher);
        AdminView adminView = new AdminView(app, refresher);

        tabs.getTabs().addAll(
                new Tab("Customer", customerView.build()),
                new Tab("Chef", chefView.build()),
                new Tab("Admin", adminView.build()));

        refresher.register(customerView);
        refresher.register(chefView);
        refresher.register(adminView);

        BorderPane root = new BorderPane();
        root.setTop(header());
        root.setCenter(tabs);

        Scene scene = new Scene(root, 1060, 720);
        scene.getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/fx/app.css")).toExternalForm());

        stage.setTitle("Special Cook Management System");
        // Dialogs are separate windows with their own icon lists, so each one applies it too -
        // see AppIcon.
        AppIcon.applyTo(stage);
        stage.setScene(scene);
        stage.setMinWidth(900);
        stage.setMinHeight(620);
        stage.show();
    }

    private VBox header() {
        Label title = new Label("Special Cook Management System");
        title.getStyleClass().add("app-title");

        Label subtitle = new Label(
                "In-memory kitchen management. Sample data is loaded: try Layla (vegan, milk allergy) "
                        + "ordering the Beef Burger or Mac and Cheese.");
        subtitle.getStyleClass().add("app-subtitle");
        subtitle.setWrapText(true);

        VBox header = new VBox(title, subtitle);
        header.getStyleClass().add("app-header");
        return header;
    }
}
