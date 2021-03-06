package com.matburt.mobileorg.Gui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.markupartist.android.widget.ActionBar;
import com.markupartist.android.widget.ActionBar.Action;
import com.markupartist.android.widget.ActionBar.IntentAction;
import com.matburt.mobileorg.R;
import com.matburt.mobileorg.Parsing.MobileOrgApplication;
import com.matburt.mobileorg.Parsing.NodeWrapper;
import com.matburt.mobileorg.Parsing.OrgFile;
import com.matburt.mobileorg.Services.SyncService;
import com.matburt.mobileorg.Settings.SettingsActivity;
import com.matburt.mobileorg.Settings.WizardActivity;
import com.matburt.mobileorg.Synchronizers.Synchronizer;

public class OutlineActivity extends ListActivity
{	

    private static final int SYNC_OPTION = 0;
    private static final int SETTINGS_OPTION = 1;
    private static final int CAPTURE_OPTION = 2;
    private static final int WEBSITE_OPTION = 3;

    public class HashMapAdapter extends BaseAdapter {
        private LinkedHashMap<String, String> mData = new LinkedHashMap<String, String>();
        private OutlineActivity mAct;
        private String[] mKeys;
        private LayoutInflater mInflater;

        public HashMapAdapter(LinkedHashMap<String, String> data, OutlineActivity act){
            mData  = data;
            mAct = act;
            mKeys = mData.keySet().toArray(new String[data.size()]);
            mInflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
            public int getCount() {
            return mData.size();
        }

        @Override
            public Object getItem(int position) {
            return mData.get(mKeys[position]);
        }

        @Override
            public long getItemId(int arg0) {
            return arg0;
        }

        @Override
            public View getView(int pos, View convertView, ViewGroup parent) {
            String key = mKeys[pos];
            String value = getItem(pos).toString();

            View row;
 
            row = mInflater.inflate(R.layout.simple_list_item, null);
 
            TextView slitem = (TextView) row.findViewById(R.id.sl_item);
            slitem.setText(key);
            TextView slinfo = (TextView) row.findViewById(R.id.sl_info);
            slinfo.setText(value);
            return row;
        }
    }


    private static final int NEW_USER_DIALOG = 0;
    private static final int UPGRADE_DIALOG = 1;

	private MobileOrgApplication appInst;

	private long node_id;
	
	/**
	 * Keeps track of the last selected item chosen from the outline. When the
	 * outline resumes it will remember what node was selected. Purely cosmetic
	 * feature.
	 */
	private int lastSelection = 0;
	
	private OutlineCursorAdapter outlineAdapter;
	private SynchServiceReceiver syncReceiver;

    private boolean emptylist = false;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.outline);		
		setupActionbar(this);
		
		this.appInst = (MobileOrgApplication) this.getApplication();
		
		Intent intent = getIntent();
		node_id = intent.getLongExtra("node_id", -1);

		if(this.node_id == -1) {
			if(this.appInst.isSyncConfigured() == false) {
                this.showWizard();
            }
            else {
                if (!this.checkVersionCode()) {
                    this.showUpgradePopup();
                }
            }
		}
        else {
            if (!this.checkVersionCode()) {
                  this.showUpgradePopup();
            }
        }

		registerForContextMenu(getListView());	
		
		this.syncReceiver = new SynchServiceReceiver();
		registerReceiver(this.syncReceiver, new IntentFilter(
				Synchronizer.SYNC_UPDATE));
				
		refreshDisplay();
	}

    private void showUpgradePopup() {
        Log.i("MobileOrg", "Showing upgrade");
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(this.getRawContents(R.raw.upgrade));
        builder.setCancelable(false);
		builder.setPositiveButton(R.string.ok,
                                  new DialogInterface.OnClickListener() {
                                      public void onClick(DialogInterface dialog, int id) {
                                          dialog.dismiss();
                                      }
                                  });
		builder.create().show();
    }

    private String getRawContents(int resource) {
        InputStream is = this.getResources().openRawResource(resource);
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String readLine = null;
        String contents = "";

        try {
            // While the BufferedReader readLine is not null 
            while ((readLine = br.readLine()) != null) {
                contents += readLine + "\n";
            }

            // Close the InputStream and BufferedReader
            is.close();
            br.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
        return contents;
    }

    private boolean checkNewInstallation() {
        SharedPreferences appSettings =
            PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        int versionCode = appSettings.getInt("appVersion", 0);
        if (versionCode == 0) {
            return false;
        }
        return true;
    }

    private boolean checkVersionCode() {
        SharedPreferences appSettings =
            PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        SharedPreferences.Editor editor = appSettings.edit();
        int versionCode = appSettings.getInt("appVersion", 0);
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            int newVersion = pInfo.versionCode;
            if (versionCode != newVersion) {
                editor.putInt("appVersion", newVersion);
                editor.commit();
                return false;
            }
        } catch (Exception e) { };
        return true;
    }
	
	public static void setupActionbar(Activity activity) {
		ActionBar actionBar = (ActionBar) activity.findViewById(R.id.actionbar);
		actionBar.setTitle("MobileOrg");

		actionBar.setHomeAction(new IntentAction(activity, new Intent(activity,
				OutlineActivity.class), R.drawable.icon));
        
		Intent intent2 = new Intent(activity, NodeEditActivity.class);
		intent2.putExtra("actionMode", NodeEditActivity.ACTIONMODE_CREATE);
        final Action otherAction = new IntentAction(activity, intent2, R.drawable.ic_menu_compose);
        actionBar.addAction(otherAction);
	}
	
	@Override
	public void onDestroy() {
		unregisterReceiver(this.syncReceiver);
		this.appInst = null;
		super.onDestroy();
	}
		
	/**
	 * Refreshes the outline display. Should be called when the underlying 
	 * data has been updated.
	 */
	private void refreshDisplay() {
		Cursor cursor;
		if (node_id >= 0) {
			cursor = appInst.getDB().getNodeChildren(node_id);
			if(cursor.getCount() == 0)
				finish();
		}
		else
			cursor = appInst.getDB().getFileCursor();

        if (cursor == null || cursor.getCount() < 1) {
            emptylist = true;
            LinkedHashMap<String, String> lhm = new LinkedHashMap<String, String>();
            lhm.put("Synchronize", "Fetch your org data");
            lhm.put("Settings", "Configure MobileOrg");
            lhm.put("Capture", "Capture a new note");
            lhm.put("Website", "Visit the MobileOrg Wiki");
            setListAdapter(new HashMapAdapter(lhm, this));
        }
        else {
            emptylist = false;
            startManagingCursor(cursor);
				
            this.outlineAdapter = new OutlineCursorAdapter(this, cursor, appInst.getDB());
            this.setListAdapter(outlineAdapter);
		
            getListView().setSelection(this.lastSelection);
        }
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.outline_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_sync:
			runSync();
			return true;
		
		case R.id.menu_settings:
			return runShowSettings();
		
		case R.id.menu_outline:
			runExpandSelection(-1);
			return true;

		case R.id.menu_capture:
			return runEditNewNodeActivity();
			
		case R.id.menu_search:
			return runSearch();
			
		case R.id.menu_help:
			runHelp();
			return true;
			
		}
		return false;
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.outline_contextmenu, menu);
		
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
		long clicked_node_id = getListAdapter().getItemId(info.position);
		
		// Prevents editing of file nodes.
		if (this.node_id == -1) {
			menu.findItem(R.id.contextmenu_edit).setVisible(false);
		} else {
			if (new NodeWrapper(clicked_node_id, appInst.getDB()).getFileName(
					appInst.getDB()).equals(OrgFile.CAPTURE_FILE)) {
				menu.findItem(R.id.contextmenu_node_delete).setVisible(true);
			}
			menu.findItem(R.id.contextmenu_delete).setVisible(false);
		}
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
				.getMenuInfo();
		
		long node_id = getListAdapter().getItemId(info.position);

		switch (item.getItemId()) {
		case R.id.contextmenu_view:
			runViewNodeActivity(node_id);
			break;

		case R.id.contextmenu_edit:
			runEditNodeActivity(node_id);
			break;
			
		case R.id.contextmenu_delete:
			runDeleteFileNode(node_id);
			break;
			
		case R.id.contextmenu_node_delete:
			runDeleteNode(node_id);
			break;
		}

		return false;
	}

	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
        if (emptylist) {
            if (position == SYNC_OPTION) {
                this.runSync();
            }
            else if (position == SETTINGS_OPTION) {
                this.runShowSettings();
            }
            else if (position == CAPTURE_OPTION) {
                this.runEditNewNodeActivity();
            }
            else if (position == WEBSITE_OPTION) {
                String url = "https://github.com/matburt/mobileorg-android/wiki";
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                startActivity(i);
            }
            return;
        }

		Long node_id = l.getItemIdAtPosition(position);
		this.lastSelection = position;
		if (this.appInst.getDB().hasNodeChildren(node_id))
			runExpandSelection(node_id);
		else
			runViewNodeActivity(node_id);
	}

    private void showWizard() {
        startActivityForResult(new Intent(this, WizardActivity.class), 0);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (!this.checkVersionCode()) {
            this.showUpgradePopup();
        }
    }
    
    private void runHelp() {
		Intent intent = new Intent(Intent.ACTION_VIEW,
				Uri.parse("https://github.com/matburt/mobileorg-android/wiki"));
    	startActivity(intent);
    }
    
    private void runSync() {
		startService(new Intent(this, SyncService.class));
    }
	
	private boolean runEditNewNodeActivity() {
		Intent intent = new Intent(this, NodeEditActivity.class);
		intent.putExtra("actionMode", NodeEditActivity.ACTIONMODE_CREATE);
		startActivity(intent);
		return true;
	}
	
	private void runEditNodeActivity(long nodeId) {
		Intent intent = new Intent(this,
				NodeEditActivity.class);
		intent.putExtra("actionMode", NodeEditActivity.ACTIONMODE_EDIT);
		intent.putExtra("node_id", nodeId);
		startActivity(intent);
	}

	private void runViewNodeActivity(long nodeId) {		
		Intent intent = new Intent(this, NodeViewActivity.class);
		intent.putExtra("node_id", nodeId);
		startActivity(intent);
	}

	private void runExpandSelection(long id) {
		Intent intent = new Intent(this, OutlineActivity.class);
		intent.putExtra("node_id", id);
		startActivity(intent);
	}
	
	private void runDeleteFileNode(final long node_id) {	
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(R.string.outline_delete_prompt)
				.setCancelable(false)
				.setPositiveButton(R.string.yes,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								appInst.getDB().removeFile(node_id);
								refreshDisplay();
							}
						})
				.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						dialog.cancel();
					}
				});
		builder.create().show();
	}
	
	private void runDeleteNode(final long node_id) {	
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(R.string.outline_delete_prompt)
				.setCancelable(false)
				.setPositiveButton(R.string.yes,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								appInst.getDB().deleteNode(node_id);
								refreshDisplay();
							}
						})
				.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						dialog.cancel();
					}
				});
		builder.create().show();
	}

	private boolean runSearch() {
		return onSearchRequested();
	}
	
	private boolean runShowSettings() {
		Intent intent = new Intent(this, SettingsActivity.class);
		startActivity(intent);
		return true;
	}

	private class SynchServiceReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getBooleanExtra(Synchronizer.SYNC_DONE, false)) {
				if (intent.getBooleanExtra("showToast", true))
					Toast.makeText(context,
							R.string.outline_synchronization_successful,
							Toast.LENGTH_SHORT).show();
				refreshDisplay();
			}
		}
	}
	

//	@Override
//	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
//
//		switch (requestCode) {
//		case NodeEncryption.DECRYPT_MESSAGE:
//			if (resultCode != RESULT_OK || intent == null)
//				return;
//			
//			Node node = this.appInst.nodestackTop();
//			this.appInst.popNodestack();
//			parseEncryptedNode(intent, node);
//			this.runExpandSelection(node);
//			break;
//		}
//	}
//	/**
//	 * This calls startActivityForResult() with Encryption.DECRYPT_MESSAGE. The
//	 * result is handled by onActivityResult() in this class, which calls a
//	 * function to parse the resulting plain text file.
//	 */
//	private void runDecryptAndExpandNode(Node node) {
//		// if suitable APG version is installed
//		if (NodeEncryption.isAvailable((Context) this)) {
//			// retrieve the encrypted file data
//			OrgFile orgfile = new OrgFile(node.name, getBaseContext());
//			byte[] rawData = orgfile.getRawFileData();
//			// save node so parsing function knows which node to parse into.
//			appInst.pushNodestack(node);
//			// and send it to APG for decryption
//			NodeEncryption.decrypt(this, rawData);
//		}
//	}
//
//	/**
//	 * This function is called with the results of
//	 * {@link #runDecryptAndExpandNode}.
//	 */
//	private void parseEncryptedNode(Intent data, Node node) {
//		OrgFileParser ofp = new OrgFileParser(getBaseContext(), appInst);
//
//		String decryptedData = data
//				.getStringExtra(NodeEncryption.EXTRA_DECRYPTED_MESSAGE);
//
//		//ofp.parse(node, new BufferedReader(new StringReader(decryptedData)));
//	}
}
