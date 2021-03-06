package com.eclipserunner.views.actions;

import com.eclipserunner.model.ILaunchNode;
import com.eclipserunner.model.INodeSelection;

/**
 * Action responsible for launching selected configuration.
 *
 * @author scharf
 */
public class DefaultLaunchConfigurationAction extends LaunchConfigurationAction {
	public DefaultLaunchConfigurationAction(INodeSelection selection, String launchGroupId) {
		super(selection, launchGroupId);
	}

	@Override
	public void run() {
		if(selection.hasExactlyOneNode() && selection.firstNodeHasType(ILaunchNode.class)) {
			ILaunchNode launcNode = selection.getFirstNodeAs(ILaunchNode.class);
			if (launcNode!=null) {
				String mode = null;
				if(launcNode.getDefaultMode()!=null) {
					mode = launcNode.getDefaultMode();
				}
				// if there is no previous launch mode, use  the default
				if(mode == null || mode.length() == 0){
					mode = getLaunchMode();
				}
				launcNode.launch(mode);
			}
		}
	}

}
