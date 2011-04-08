package com.jadn.cc.ui;

import android.app.Activity;
import com.jadn.cc.core.CarCastApplication;
import com.jadn.cc.core.ContentServiceListener;
import com.jadn.cc.services.ContentService;
import com.jadn.cc.services.PlayStatusListener;
import com.jadn.cc.services.SubscriptionHelper;


public abstract class BaseActivity extends Activity implements ContentServiceListener, PlayStatusListener {
	protected ContentService contentService;

	protected void onContentService() {
	    // does nothing by default
	}

	@Override
	public void onContentServiceChanged(ContentService service) {
		if (contentService != null) {
			contentService.setPlayStatusListener(null);
		}
	    contentService = service;
	    if (service != null) {
	    	service.setPlayStatusListener(this);
            onContentService();
        }
	}

	@Override
	protected void onResume() {
	    super.onResume();
	    getCarCastApplication().setContentServiceListener(this);
	}

    protected CarCastApplication getCarCastApplication() {
        return ((CarCastApplication)getApplication());
    }

	@Override
	public void playStateUpdated(boolean playing) {
		// default implementation does nothing
	}

	protected SubscriptionHelper getSubscriptionHelper() {
		return getCarCastApplication().getSubscriptionHelper();
	}
}
