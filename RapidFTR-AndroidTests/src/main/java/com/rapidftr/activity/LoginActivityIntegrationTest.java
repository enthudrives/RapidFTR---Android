package com.rapidftr.activity;

import com.rapidftr.R;
import com.rapidftr.activity.pages.LoginPage;

import static com.rapidftr.activity.pages.LoginPage.*;

public class LoginActivityIntegrationTest extends BaseActivityIntegrationTest {

    public void testIncorrectLoginCredentials(){
        loginPage.login("wrongUsername", "wrongPassword", LOGIN_URL);
        assertTrue(solo.waitForText("Incorrect username or password"));
    }

    public void testIncorrectServer() {
        loginPage.login(USERNAME, PASSWORD, LoginPage.LOGIN_URL+":abc");
        solo.waitForText("Unable to connect to the server, please contact your system administrator");
    }

    public void testNoLoginDetailsErrorMessages(){
          loginPage.login("","","");
          assertTrue(loginPage.getUserNameRequiredMessage().equals("Username is required"));
          assertTrue(loginPage.getPasswordRequiredMEssage().equals("Password is required"));
          assertTrue(loginPage.getURLRequiredMessage().equals("Server URL is required"));
    }

     public void testUserAbleToSeeLastSuccessfulLoginUrl() {
         loginPage.login(USERNAME, PASSWORD, LOGIN_URL);
         solo.waitForText("Login Successful");
         loginPage.logout();
         loginPage.changeURL();
         assertTrue(loginPage.getUrl().equals(LOGIN_URL));
    }

    public void testCannotNavigateBackToLoginPageOnceLoggedIn(){
        loginPage.login();
        solo.waitForText("Login Successful");
        solo.goBack();
        solo.assertCurrentActivity("should still be on the home page", MainActivity.class);
        loginPage.logout();
    }

    public void testUserIsAlertedWhenAttemptingToLogoutWhileSyncInProgress() throws InterruptedException {
        loginPage.login();
        solo.waitForText("Login Successful");
        solo.clickOnMenuItem(solo.getString(R.string.synchronize_all));
        solo.sleep(1000);
        solo.clickOnMenuItem(solo.getString(R.string.log_out));

        assertTrue("Could not find the dialog!", solo.searchText(solo.getString(R.string.confirm_logout_message)));

        solo.clickOnButton(solo.getString(R.string.log_out));
        solo.assertCurrentActivity("should log out and go to login page", LoginActivity.class);
    }

}
