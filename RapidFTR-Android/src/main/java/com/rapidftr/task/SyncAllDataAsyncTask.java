package com.rapidftr.task;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;
import com.google.inject.Inject;
import com.rapidftr.R;
import com.rapidftr.RapidFtrApplication;
import com.rapidftr.activity.RapidFtrActivity;
import com.rapidftr.model.Child;
import com.rapidftr.repository.ChildRepository;
import com.rapidftr.service.ChildService;
import com.rapidftr.service.FormService;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.System.currentTimeMillis;

public class SyncAllDataAsyncTask extends AsyncTask<Void, String, Boolean> {

    public static final int NOTIFICATION_ID = 1010;
    private FormService formService;
    private ChildService childService;
    private ChildRepository childRepository;
    private RapidFtrActivity context;
    private static final int MAX_PROGRESS = 100;
    private Notification notification;
    private NotificationManager notificationManager;
    private static final String SYNC_ALL = "SYNC_ALL";
    private static final String CANCEL_SYNC_ALL = "CANCEL_SYNC_ALL";
    private static final int FORM_SECTION_PROGRESS = 20;

    @Inject
    public SyncAllDataAsyncTask(FormService formService, ChildService childService, ChildRepository childRepository) {
        this.formService = formService;
        this.childService = childService;
        this.childRepository = childRepository;
    }

    @Override
    protected void onPreExecute() {
        RapidFtrApplication.getApplicationInstance().setSyncTask(this);
        toggleMenu(CANCEL_SYNC_ALL);
        initNotifiers();
        configureNotification();
    }

    private void configureNotification() {
        Intent intent = new Intent(context, RapidFtrActivity.class);
        notification.flags = notification.flags | Notification.FLAG_ONGOING_EVENT;
        notification.contentIntent = PendingIntent.getActivity(context, 0, intent, 0);
        notification.contentView = new RemoteViews(context.getPackageName(), R.layout.progress_bar);
    }

    @Override
    protected Boolean doInBackground(Void... notRelevant) {
        try {
            if (!isCancelled()) {
                setProgressAndNotify("Step 1 of 3 - Syncing Form Sections...", 0);
                formService.getPublishedFormSections();
            }
            setProgressAndNotify("Step 2 of 3 - Sending records to server...", FORM_SECTION_PROGRESS);
            ArrayList<String> idsToDownload = getAllIdsForDownload();
            List<Child> childrenToSyncWithServer = childRepository.toBeSynced();

            int uploadMaxProgressLimit = calculateUploadMaxProgress(idsToDownload, childrenToSyncWithServer);
            sendChildrenToServer(childrenToSyncWithServer, uploadMaxProgressLimit);
            saveIncomingChildren(idsToDownload, uploadMaxProgressLimit);
            setProgressAndNotify("Sync complete.", MAX_PROGRESS);
        } catch (Exception e) {
            Log.e("SyncAllDataTask", "Error in sync", e);
            publishProgress("Error in syncing. Try again after some time.");
            return false;
        }
        return true;
    }

    private int calculateUploadMaxProgress(ArrayList<String> idsToDownload, List<Child> childrenToSyncWithServer) {
        int numberOfRecordsToDownload = idsToDownload.size();
        int numberOfRecordsToUpload = childrenToSyncWithServer.size();
        int totalRecords = numberOfRecordsToDownload + numberOfRecordsToUpload;
        return (numberOfRecordsToUpload / totalRecords) * 80;
    }

    public ArrayList<String> getAllIdsForDownload() throws IOException, JSONException {
        HashMap<String,String> serverIdsRevs = childService.getAllIdsAndRevs();
        HashMap<String, String> repoIdsAndRevs = childRepository.getAllIdsAndRevs();
        ArrayList<String> idsToDownload = new ArrayList<String>();
        for(Map.Entry<String,String> serverIdRev : serverIdsRevs.entrySet()){
            if(!isServerIdExistingInRepository(repoIdsAndRevs, serverIdRev) || (repoIdsAndRevs.get(serverIdRev.getKey()) != null && isRevisionMismatch(repoIdsAndRevs, serverIdRev))){
                idsToDownload.add(serverIdRev.getKey());
            }
        }
        return idsToDownload;
    }

    private boolean isRevisionMismatch(HashMap<String, String> repoIdsAndRevs, Map.Entry<String, String> serverIdRev) {
        return !repoIdsAndRevs.get(serverIdRev.getKey()).equals(serverIdRev.getValue());
    }

    private boolean isServerIdExistingInRepository(HashMap<String, String> repoIdsAndRevs, Map.Entry<String, String> serverIdRev) {
        return repoIdsAndRevs.get(serverIdRev.getKey()) != null;
    }

    @Override
    protected void onProgressUpdate(String... values) {
        Toast.makeText(RapidFtrApplication.getApplicationInstance(), values[0], Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onPostExecute(Boolean result) {
        RapidFtrApplication.getApplicationInstance().setSyncTask(null);
        toggleMenu(SYNC_ALL);
        notificationManager.cancel(NOTIFICATION_ID);
    }

    @Override
    protected void onCancelled() {
        RapidFtrApplication.getApplicationInstance().setSyncTask(null);
        toggleMenu(SYNC_ALL);
        notificationManager.cancel(NOTIFICATION_ID);
    }

    private void toggleMenu(String showMenu) {
        context.getMenu().getItem(0).setVisible(showMenu == SYNC_ALL);
        context.getMenu().getItem(1).setVisible(showMenu == CANCEL_SYNC_ALL);
    }

    private void initNotifiers() {
        notification = new Notification(R.drawable.icon, "Syncing in progress...", currentTimeMillis());
        notificationManager = (NotificationManager) context.getSystemService(context.NOTIFICATION_SERVICE);
    }

    private void setProgressAndNotify(String statusText, int progress) {
        if (!isCancelled()) {
            notification.contentView.setTextViewText(R.id.status_text, statusText);
            notification.contentView.setProgressBar(R.id.status_progress, MAX_PROGRESS, progress, false);
            notificationManager.notify(NOTIFICATION_ID, notification);
        }
    }

    private void sendChildrenToServer(List<Child> childrenToSyncWithServer, int maxProgress) throws IOException, JSONException {
        String subStatusFormat = "Uploading Child %s of " + childrenToSyncWithServer.size();
        int counter = 0;
        int progressStep = childrenToSyncWithServer.size() == 0 ? 0 : maxProgress / childrenToSyncWithServer.size();
        int startProgress = progressStep + FORM_SECTION_PROGRESS;
        for (Child child : childrenToSyncWithServer) {
            if (isCancelled()) {
                break;
            }
            childService.sync(child);
            setProgressAndNotify(String.format(subStatusFormat, ++counter), startProgress);
            startProgress += progressStep;
        }
    }

    private void saveIncomingChildren(ArrayList<String> idsToDownload, int maxProgressed) throws IOException, JSONException {
        String subStatusFormat = "Downloading Child %s of" + idsToDownload.size();
        int counter = 0;
        int currentProgress = (MAX_PROGRESS - maxProgressed) / idsToDownload.size();
        int startProgress = currentProgress + FORM_SECTION_PROGRESS ;
        setProgressAndNotify("Step 3 of 3 - Bringing down records from server...", startProgress);

        for (String idToDownload : idsToDownload) {
            Child incomingChild = childService.getChild(idToDownload);
            if (isCancelled()) {
                break;
            }
            try {
                incomingChild.setSynced(true);
                if (childRepository.exists(incomingChild.getUniqueId())) {
                    childRepository.update(incomingChild);
                } else {
                    childRepository.createOrUpdate(incomingChild);
                }
                childService.setPhoto(incomingChild);
                setProgressAndNotify(String.format(subStatusFormat, ++counter), startProgress);
                startProgress += currentProgress;
            } catch (Exception e) {
                Log.e("SyncAllDataTask", "Error syncing child", e);
                throw new RuntimeException(e);
            }
        }
    }

    public void setContext(RapidFtrActivity context) {
        this.context = context;
    }
}
