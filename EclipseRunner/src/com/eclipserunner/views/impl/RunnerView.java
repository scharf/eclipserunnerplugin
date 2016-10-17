package com.eclipserunner.views.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Platform;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationListener;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.internal.ui.launchConfigurations.LaunchGroupExtension;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.util.LocalSelectionTransfer;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.ITreeViewerListener;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreeExpansionEvent;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.dialogs.FilteredTree;
import org.eclipse.ui.dialogs.PatternFilter;
import org.eclipse.ui.part.ViewPart;

import com.eclipserunner.Messages;
import com.eclipserunner.RunnerPlugin;
import com.eclipserunner.model.ICategoryNode;
import com.eclipserunner.model.IExpandable;
import com.eclipserunner.model.IFilteredRunnerModel;
import com.eclipserunner.model.ILaunchNode;
import com.eclipserunner.model.IModelChangeListener;
import com.eclipserunner.model.RunnerModelProvider;
import com.eclipserunner.model.adapters.RunnerModelLaunchConfigurationListenerAdapter;
import com.eclipserunner.model.adapters.RunnerProjectSelectionListenerAdapter;
import com.eclipserunner.ui.dnd.RunnerViewDragListener;
import com.eclipserunner.ui.dnd.RunnerViewDropListener;
import com.eclipserunner.views.IRunnerView;
import com.eclipserunner.views.TreeMode;
import com.eclipserunner.views.actions.LaunchActionBuilder;
import com.eclipserunner.views.actions.LaunchOtherConfigurationAction;
import com.eclipserunner.views.actions.RunDefaultAction;
import com.eclipserunner.views.actions.ShowLaunchOtherConfigurationsDialogAction;

/**
 * Class provides plugin eclipse View UI component.
 *
 * @author vachacz, bary
 */
@SuppressWarnings("restriction")
public class RunnerView extends ViewPart
	implements IMenuListener, IDoubleClickListener, IModelChangeListener, IRunnerView {

	private IFilteredRunnerModel runnerModel;

	private TreeViewer viewer;

	private ILaunchConfigurationListener modelLaunchConfigurationListener;
	private ILaunchConfigurationListener viewLaunchConfigurationListener;
	private ISelectionListener selectionListener;

	private List<ShowLaunchOtherConfigurationsDialogAction> showLaunchOtherConfigurationActions = new ArrayList<ShowLaunchOtherConfigurationsDialogAction>();

	private Action launchDefaultConfigurationAction;
	private List<LaunchOtherConfigurationAction> launchOtherConfigurationActions = new ArrayList<LaunchOtherConfigurationAction>();
	private Action openItemAction;

	private Action addNewCategoryAction;

	private Action collapseAllAction;
	private Action expandAllAction;

	private Action bookmarkAction;
	private Action unbookmarkAction;

	private Action renameAction;
	private Action removeAction;

	private Action toggleFlatModeAction;
	private Action toggleTypeModeAction;
	private Action toggleDefaultCategoryAction;
	private RunDefaultAction RunDefaultActionAction;
	private Action toggleBookmarkModeAction;

	private Action toggleClosedProjectFilterAction;
	private Action toggleDelectedProjectFilterAction;
//	private Action toggleActiveWorkingSetFilterAction;
	private Action toggleActiveProjektFilterAction;

	private LaunchActionBuilder builder;
	private RunnerViewSelection selection;

	@Override
	public void createPartControl(Composite parent) {
		initializeModel();
		initializeViewer(parent);

		selection = new RunnerViewSelection(getViewer());

		initializeSelectionListeners();
		initializeLaunchConfigurationListeners();
		initializeResourceChangeListener();
		initializeDragAndDrop();

		setupActionBuilder();
		setupLaunchActions();
		setupContextMenu();
		setupActionBars();
		setupRunMenu();
	}


	private void initializeModel() {
		runnerModel = RunnerModelProvider.getInstance().getFilteredModel();
		runnerModel.addModelChangeListener(this);
	}

	private void initializeViewer(Composite parent) {
		PatternFilter patternFilter = new PatternFilter();
		patternFilter.setIncludeLeadingWildcard(true);

		FilteredTree tree = new FilteredTree(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL, patternFilter, true);

		viewer = tree.getViewer();
		setupTreeContentProvider();
		viewer.setLabelProvider(new LaunchTreeLabelProvider(runnerModel));
		viewer.addDoubleClickListener(this);
		viewer.setInput(getViewSite());
		viewer.addTreeListener(new ITreeViewerListener() {
			public void treeExpanded(TreeExpansionEvent event) {
				Object element = event.getElement();
				if(element instanceof IExpandable) {
					((IExpandable) element).setExpanded(true);
				}
			}
			public void treeCollapsed(TreeExpansionEvent event) {
				Object element = event.getElement();
				if(element instanceof IExpandable) {
					((IExpandable) element).setExpanded(false);
				}
			}
		});
		viewer.getTree().addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent ke) {
				if (ke.character == SWT.DEL) {
					removeAction.run();
				}
			}
		});
		// we're cooperative and also provide our selection
		getSite().setSelectionProvider(viewer);
		updateExpansion();
	}

	private void initializeSelectionListeners() {
		selectionListener = new RunnerProjectSelectionListenerAdapter(runnerModel, this);
		getSite().getWorkbenchWindow().getSelectionService().addPostSelectionListener(selectionListener);
	}

	private void initializeLaunchConfigurationListeners() {
		viewLaunchConfigurationListener = new ILaunchConfigurationListener() {
			public void launchConfigurationRemoved(ILaunchConfiguration configuration) {
				refresh();
			}
			public void launchConfigurationChanged(ILaunchConfiguration configuration) {
				refresh();
			}
			public void launchConfigurationAdded(ILaunchConfiguration configuration) {
				refresh();
			}
		};

		modelLaunchConfigurationListener = new RunnerModelLaunchConfigurationListenerAdapter(runnerModel);

		getLaunchManager().addLaunchConfigurationListener(viewLaunchConfigurationListener);
		getLaunchManager().addLaunchConfigurationListener(modelLaunchConfigurationListener);
	}

	private void initializeResourceChangeListener() {
		ResourcesPlugin.getWorkspace().addResourceChangeListener(new IResourceChangeListener() {
			public void resourceChanged(IResourceChangeEvent event) {
				refresh();
			}
		}, IResourceChangeEvent.POST_CHANGE);

	}

	private void initializeDragAndDrop() {
		int operations = DND.DROP_COPY | DND.DROP_MOVE;
		Transfer[] transferTypes = new Transfer[] {
			LocalSelectionTransfer.getTransfer(), FileTransfer.getInstance()
		};

		getViewer().addDragSupport(operations, transferTypes, new RunnerViewDragListener(getViewer()));
		getViewer().addDropSupport(operations, transferTypes, new RunnerViewDropListener(getViewer()));
	}

	@Override
	public void dispose() {
		super.dispose();

		disposeModel();
		disposeSelectionListeners();
		disposeLaunchConfigurationListeners();
	}

	private void disposeModel() {
		runnerModel.removeModelChangeListener(this);
	}

	private void disposeSelectionListeners() {
		getSite().getWorkbenchWindow().getSelectionService().removePostSelectionListener(selectionListener);
	}

	private void disposeLaunchConfigurationListeners() {
		getLaunchManager().removeLaunchConfigurationListener(modelLaunchConfigurationListener);
		getLaunchManager().removeLaunchConfigurationListener(viewLaunchConfigurationListener);
	}

	private void setupLaunchActions() {
		launchDefaultConfigurationAction   = builder.createDefaultConfigurationAction();

		setupLaunchOpenActions();
		setupLaunchExecuteActions();

		openItemAction                      = builder.createOpenItemAction();
		addNewCategoryAction                = builder.createAddNewCategoryAction();
		collapseAllAction                   = builder.createCollapseAllAction(this);
		expandAllAction                     = builder.createExpandAllAction(this);
		bookmarkAction                      = builder.createBookmarkAction();
		unbookmarkAction                    = builder.createUnbookmarkAction();
		renameAction                        = builder.createRenameAction();
		removeAction                        = builder.createRemoveAction();
		toggleFlatModeAction                = builder.createToggleFlatModeAction();
		toggleTypeModeAction                = builder.createToggleTypeModeAction();
		toggleDefaultCategoryAction         = builder.createToggleDefaultCategoryAction();
		RunDefaultActionAction              = builder.createRunDefaultAction();
		toggleBookmarkModeAction            = builder.createToggleBookmarkModeAction();
		toggleClosedProjectFilterAction     = builder.createToggleClosedProjectFilterAction();
		toggleDelectedProjectFilterAction   = builder.createDelectedProjectFilterAction();
//		toggleActiveWorkingSetFilterAction  = builder.createActiveWorkingSetFilterAction();
		toggleActiveProjektFilterAction     = builder.createActiveProjektFilterAction();
	}

	private String getCompareModeString(IConfigurationElement e) {
		String mode = e.getAttribute("mode");
		if(ILaunchManager.RUN_MODE.equals(mode)) {
			return " 1";
		} else if(ILaunchManager.DEBUG_MODE.equals(mode)) {
			return " 2";
		} else if(ILaunchManager.PROFILE_MODE.equals(mode)) {
			return " 3";
		}
		return e.getAttribute("label").replaceAll("&", "");
	}
	/**
	 * Sorts the configuration elements by modes. It makes sure that the run, debug, profile mode
	 * comes first in that order....
	 * @param elements
	 */
	private void sortModes(IConfigurationElement[] elements) {
		Arrays.sort(elements, new Comparator<IConfigurationElement>() {
			public int compare(IConfigurationElement o1,
					IConfigurationElement o2) {
				return getCompareModeString(o1).compareTo(getCompareModeString(o2));
			}
		});
	}
	/**
	 * Creates 'launch' and 'open configuration' actions for each launch group
	 */
	private void setupLaunchExecuteActions() {
		// see https://www.eclipse.org/articles/Article-Launch-Framework/launch.html
		IExtensionRegistry reg = Platform.getExtensionRegistry();
		IConfigurationElement[] launchGroups = reg.getConfigurationElementsFor("org.eclipse.debug.ui.launchGroups");
		IConfigurationElement[] launchModes = reg.getConfigurationElementsFor("org.eclipse.debug.core.launchModes");
		sortModes(launchGroups);
		for( IConfigurationElement launchGroup : launchGroups ) {
			String category = launchGroup.getAttribute("category");
			String mode = launchGroup.getAttribute("mode");
			LaunchGroupExtension launchGroupExtension = new LaunchGroupExtension(launchGroup);
			ImageDescriptor image = launchGroupExtension.getImageDescriptor();
			String label = launchGroup.getAttribute("label");
			// we need to find the label (Run, Debug etc) in the launch modes....
			for( IConfigurationElement launchMode : launchModes ) {
				if(mode.equals(launchMode.getAttribute("mode"))) {
					label = launchMode.getAttribute("label");
					break;
				}
			}
			launchOtherConfigurationActions.add(builder.createLaunchOtherConfigurationAction(
					mode, category, label, label, image
			));
		}
	}

	
	/**
	 * Sorts elements first by category (with null category first) and then by mode.
	 * @param elements
	 */
	private void sortCategoryModes(IConfigurationElement[] elements) {
		Arrays.sort(elements, new Comparator<IConfigurationElement>() {
			private String getCompareCategoryString(IConfigurationElement e) {
				String category = e.getAttribute("category");
				if(category==null)
					category=" ";
				return category;
			}
			public int compare(IConfigurationElement o1,
					IConfigurationElement o2) {
				int result = getCompareCategoryString(o1).compareTo(getCompareCategoryString(o2));
				if(result!=0)
					return result;
				return getCompareModeString(o1).compareTo(getCompareModeString(o2));
			}
		});
	}

	/**
	 * Creates 'launch' and 'open configuration' actions for each launch group
	 */
	private void setupLaunchOpenActions() {
		// see https://www.eclipse.org/articles/Article-Launch-Framework/launch.html
		IExtensionRegistry reg = Platform.getExtensionRegistry();
		IConfigurationElement[] launchGroups = reg.getConfigurationElementsFor("org.eclipse.debug.ui.launchGroups");
		sortCategoryModes(launchGroups);
		for( IConfigurationElement launchGroup : launchGroups ) {
			LaunchGroupExtension launchGroupExtension = new LaunchGroupExtension(launchGroup);
			if( launchGroupExtension != null ) {
				String category = launchGroup.getAttribute("category");
				ImageDescriptor image = launchGroupExtension.getImageDescriptor();
				String label = launchGroup.getAttribute("label");
				String catlabel="";
				if(category!=null) {
					catlabel=" "+label;
				}
				label = String.format("Open %1$s %2$s configurations ...", catlabel, launchGroupExtension.getMode());
				showLaunchOtherConfigurationActions.add(builder.createShowLaunchOtherConfigurationDialogAction(
						launchGroupExtension, label, label, image
				));
			}
		}
	}

	private void setupActionBuilder() {
		builder = LaunchActionBuilder.newInstance()
			.withLaunchConfigurationSelection(selection)
			.withRunnerModel(runnerModel)
			.withRunnerView(this);
	}

	private void setupContextMenu() {
		MenuManager menuMgr = new MenuManager("#PopupMenu");
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(this);

		Menu menu = menuMgr.createContextMenu(getViewerControl());
		getViewerControl().setMenu(menu);
		getSite().registerContextMenu(menuMgr, viewer);
	}

	private void setupActionBars() {
		IActionBars bars = getViewSite().getActionBars();
		setupLocalPullDown(bars.getMenuManager());
		setupLocalToolBar(bars.getToolBarManager());
	}

	private void setupLocalPullDown(IMenuManager manager) {
		manager.add(addNewCategoryAction);
		manager.add(new Separator());
		manager.add(toggleFlatModeAction);
		manager.add(toggleTypeModeAction);
		manager.add(new Separator());
		manager.add(toggleClosedProjectFilterAction);
		manager.add(toggleDelectedProjectFilterAction);
//		manager.add(toggleActiveWorkingSetFilterAction);
		manager.add(toggleActiveProjektFilterAction);
		manager.add(new Separator());
		for(Action showLaunchOtherConfigurationAction : showLaunchOtherConfigurationActions) {
			manager.add(showLaunchOtherConfigurationAction);
		}
	}

	private void setupLocalToolBar(IToolBarManager manager) {
		manager.add(toggleBookmarkModeAction);
		manager.add(toggleDefaultCategoryAction);
		manager.add(RunDefaultActionAction);
		manager.add(new Separator());
		manager.add(addNewCategoryAction);
		manager.add(new Separator());
		manager.add(collapseAllAction);
		manager.add(expandAllAction);
	}

	public void menuAboutToShow(IMenuManager manager) {
		setupMenuItems(manager);
		setupActionEnablement();
	}
	private void setupMenuItems(IMenuManager manager) {
		ILaunchNode launchNode = getLaunchNode();
		if( launchNode != null) {
			setupRunMenu(manager, launchNode);
	        manager.add(new Separator());
			manager.add(openItemAction);
			manager.add(new Separator());
	        manager.add(moveToCategorySubMenu());
	        manager.add(new Separator());
		}
		manager.add(addNewCategoryAction);
		manager.add(new Separator());
		manager.add(renameAction);
		removeAction.setAccelerator(SWT.DEL);
		manager.add(removeAction);
		if( launchNode != null) {
			manager.add(new Separator());
			manager.add(bookmarkAction);
			manager.add(unbookmarkAction);
			manager.add(new Separator());
			for(ShowLaunchOtherConfigurationsDialogAction showLaunchOtherConfigurationAction : showLaunchOtherConfigurationActions) {
				String mode = showLaunchOtherConfigurationAction.getMode();
				String category = showLaunchOtherConfigurationAction.getCategory();
				if(launchNode.supportsMode(mode, category)) {
					manager.add(showLaunchOtherConfigurationAction);
				}
			}
		}
	}

	private void setupRunMenu() {
		viewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				updateRunMenu();
			}
		});
		updateRunMenu();
		
	}
	private ILaunchNode getLaunchNode() {
		ILaunchNode launchNode = null;
		if(selection.firstNodeHasType(ILaunchNode.class)) {
			launchNode=selection.getFirstNodeAs(ILaunchNode.class);
		}
		return launchNode;
	}

	private void setupRunMenu(IMenuManager manager, ILaunchNode launchNode) {
		if (launchNode != null) {
			for(LaunchOtherConfigurationAction otherLaunchAction : launchOtherConfigurationActions) {
				String mode = otherLaunchAction.getMode();
				String category = otherLaunchAction.getCategory();
				if(launchNode.supportsMode(mode, category)) {
					manager.add(otherLaunchAction);
					otherLaunchAction.setChecked(mode.equals(launchNode.getDefaultMode()));
				}
			}
		}
	}

	private MenuManager moveToCategorySubMenu() {
		MenuManager managet = new MenuManager(Messages.Message_moveToSubMenu, null);
		Collection<ICategoryNode> categories =
			RunnerModelProvider.getInstance().getDefaultModel().getCategoryNodes();
		for (ICategoryNode category : categories) {
			managet.add(moveToCategoryAction(category));
		}
		return managet;
	}

	private IAction moveToCategoryAction(ICategoryNode category) {
		return builder.createMoveToCategoryAction(selection.getSelectedNodesByType(ILaunchNode.class), category);
	}

	private void setupActionEnablement() {
		for(Action otherLaunchAction : launchOtherConfigurationActions) {
			otherLaunchAction.setEnabled(selection.canBeLaunched());
		}
		renameAction.setEnabled(selection.canBeRenamed());
		removeAction.setEnabled(selection.canBeRemoved());
		bookmarkAction.setEnabled(selection.canBeBookmarked());
		unbookmarkAction.setEnabled(selection.canBeBookmarked());
		openItemAction.setEnabled(selection.canBeOpened());
	}

	public void doubleClick(DoubleClickEvent event) {
		launchDefaultConfigurationAction.run();
	}

	public void modelChanged() {
		refresh();
		updateRunMenu();
	}

	@Override
	public void setFocus() {
		getViewerControl().setFocus();
	}

	public TreeViewer getViewer() {
		return viewer;
	}

	public ITreeContentProvider getTreeContentProvider() {
		return (ITreeContentProvider) getViewer().getContentProvider();
	}

	private Control getViewerControl() {
		return getViewer().getControl();
	}

	private ILaunchManager getLaunchManager() {
		return DebugPlugin.getDefault().getLaunchManager();
	}

	public void setTreeMode(TreeMode mode) {
		setupTreeModeActions(mode);
		setupTreeContentProvider();
		refresh();
	}

	protected void setupTreeModeActions(TreeMode mode) {
		if (mode.isHierarchical()) {
			toggleFlatModeAction.setChecked(false);
			toggleTypeModeAction.setChecked(true);
		} else {
			toggleFlatModeAction.setChecked(true);
			toggleTypeModeAction.setChecked(false);
		}
	}

	protected void setupTreeContentProvider() {
		viewer.setContentProvider(
			RunnerModelProvider.getInstance().getTreeContentProvider()
		);
	}

	public void refresh() {
		RunnerPlugin.getDisplay().syncExec(new Runnable() {
			public void run() {
				getViewer().refresh();
				updateExpansion();
			}
		});
	}

	void setTreeViewerForTesting(TreeViewer viewer) {
		this.viewer = viewer;
	}


	private void updateRunMenu() {
		IMenuManager menuManager = RunDefaultActionAction.getMenuManager();
		menuManager.removeAll();
		setupRunMenu(menuManager,getLaunchNode());
		RunDefaultActionAction.update();
	}


	private void updateExpansion() {
		Collection<Object> expanded = new ArrayList<Object>();
		ITreeContentProvider contentProvider=(ITreeContentProvider) viewer.getContentProvider();
		Collection<ICategoryNode> nodes = RunnerModelProvider.getInstance().getDefaultModel().getCategoryNodes();
		for (ICategoryNode categroy : nodes) {
			if(categroy.isExpanded()) {
				expanded.add(categroy);
			}
			for (Object child : contentProvider.getChildren(categroy)) {
				if(child instanceof IExpandable && ((IExpandable) child).isExpanded()) {
					expanded.add(child);
				}
			}
		}
		getViewer().setExpandedElements(expanded.toArray());
	}
	
	private void setExpanded(ITreeContentProvider contentProvider, Object[] children, boolean expanded) {
		if(children==null)
			return;
		for (Object child : children) {
			if(child instanceof IExpandable) {
				((IExpandable) child).setExpanded(expanded);
			}
			setExpanded(contentProvider, contentProvider.getChildren(child),expanded);
		}
	}
	
	public void expandAll() {
		viewer.expandAll();
		ITreeContentProvider contentProvider=(ITreeContentProvider) viewer.getContentProvider();
		setExpanded(contentProvider,contentProvider.getElements(viewer.getInput()),true);
	}

	public void collapseAll() {
		viewer.collapseAll();
		ITreeContentProvider contentProvider=(ITreeContentProvider) viewer.getContentProvider();
		setExpanded(contentProvider,contentProvider.getElements(viewer.getInput()),false);
	}
}