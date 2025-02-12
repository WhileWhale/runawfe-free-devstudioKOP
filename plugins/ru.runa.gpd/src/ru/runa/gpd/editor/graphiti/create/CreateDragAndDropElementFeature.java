package ru.runa.gpd.editor.graphiti.create;

import java.util.List;
import org.eclipse.draw2d.geometry.Dimension;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.gef.EditPartViewer;
import org.eclipse.gef.SharedCursors;
import org.eclipse.graphiti.features.context.IContext;
import org.eclipse.graphiti.features.context.ICreateConnectionContext;
import org.eclipse.graphiti.features.context.impl.CreateConnectionContext;
import org.eclipse.graphiti.features.context.impl.CreateContext;
import org.eclipse.graphiti.features.impl.AbstractCreateConnectionFeature;
import org.eclipse.graphiti.mm.algorithms.GraphicsAlgorithm;
import org.eclipse.graphiti.mm.algorithms.Text;
import org.eclipse.graphiti.mm.pictograms.AnchorContainer;
import org.eclipse.graphiti.mm.pictograms.Connection;
import org.eclipse.graphiti.mm.pictograms.PictogramElement;
import org.eclipse.graphiti.services.Graphiti;
import org.eclipse.ui.PlatformUI;
import ru.runa.gpd.editor.GEFConstants;
import ru.runa.gpd.editor.graphiti.CustomUndoRedoFeature;
import ru.runa.gpd.editor.graphiti.DiagramFeatureProvider;
import ru.runa.gpd.editor.graphiti.GraphitiProcessEditor;
import ru.runa.gpd.lang.NodeTypeDefinition;
import ru.runa.gpd.lang.model.Action;
import ru.runa.gpd.lang.model.GraphElement;
import ru.runa.gpd.lang.model.Node;
import ru.runa.gpd.lang.model.ProcessDefinition;
import ru.runa.gpd.lang.model.Swimlane;
import ru.runa.gpd.lang.model.SwimlanedNode;
import ru.runa.gpd.lang.model.Transition;
import ru.runa.gpd.lang.model.bpmn.IBoundaryEvent;
import ru.runa.gpd.lang.model.bpmn.IBoundaryEventContainer;

public class CreateDragAndDropElementFeature extends AbstractCreateConnectionFeature implements GEFConstants, CustomUndoRedoFeature {
    public static final String CREATE_CONTEXT = "createContext";
    private NodeTypeDefinition nodeDefinition;
    private DiagramFeatureProvider featureProvider;
    private CreateContext createContext;
    private EditPartViewer viewer;
    /**
     * The 'alreadyAdded' field is necessary to correct the situation: When creating a new element using the palette, if after clicking the mouse
     * button on the desired element in the microhelp, you do not immediately move the cursor to the point of the future placement of the element, but
     * after describing a circle inside the microhelp field, you release the button on the same element, the element itself and its duplicate are
     * blocked. The 'alreadyAdded' field changes its value to 'true' after the first element is created, a check is made for the possibility of
     * creating it in 'canCreate()', and the 'create()' method is not called again.
     */
    private boolean alreadyAdded = false;

    public CreateDragAndDropElementFeature(CreateContext createContext) {
        super(null, "", "");
        this.createContext = createContext;
        viewer = ((GraphitiProcessEditor) PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor())
                .getGraphicalViewer();
    }

    public void setNodeDefinition(NodeTypeDefinition nodeDefinition) {
        this.nodeDefinition = nodeDefinition;
    }

    public void setFeatureProvider(DiagramFeatureProvider provider) {
        this.featureProvider = provider;
    }

    @Override
    public DiagramFeatureProvider getFeatureProvider() {
        return featureProvider;
    }

    @Override
    public String getCreateName() {
        return nodeDefinition.getLabel();
    }

    @Override
    public String getCreateImageId() {
        return getNodeDefinition().getPaletteIcon();
    }

    public NodeTypeDefinition getNodeDefinition() {
        return nodeDefinition;
    }

    @Override
    public boolean canCreate(ICreateConnectionContext context) {
        boolean create = true;
        if (viewer != null && context.getTargetPictogramElement() != null) {
            GraphicsAlgorithm pictogramElement = context.getTargetPictogramElement().getGraphicsAlgorithm();
            if (context.getTargetAnchor() != null || pictogramElement instanceof Text) {
                viewer.setCursor(SharedCursors.CURSOR_TREE_MOVE);
                create = false;
            }
        }
        if (create && !alreadyAdded) {
            viewer.setCursor(SharedCursors.CURSOR_TREE_ADD);
            return true;
        }
        return false;
    }

    @Override
    public Connection create(ICreateConnectionContext createConnectionContext) {
        parent = (GraphElement) getBusinessObjectForPictogramElement(createContext.getTargetContainer());
        graphElement = getNodeDefinition().createElement(getProcessDefinition(), true);
        if (graphElement instanceof Action) {
            if (createContext.getTargetConnection() != null) {
                parent = (GraphElement) getBusinessObjectForPictogramElement(createContext.getTargetConnection());
            }
            ((Action) graphElement).setName(getCreateName() + " " + (parent.getChildren(Action.class).size() + 1));
        }
        graphElement.setUiParentContainer(parent);
        Swimlane swimlane = null;
        if (parent instanceof Swimlane) {
            swimlane = (Swimlane) parent;
            parent = parent.getParent();
        }
        if (graphElement instanceof SwimlanedNode) {
            ((SwimlanedNode) graphElement).setSwimlane(swimlane);
        }
        if ((!(parent instanceof IBoundaryEventContainer) || parent.getChildren(graphElement.getClass()).size() < 1)) {
            parent.addChild(graphElement);
            setLocationAndSize(graphElement, createContext, (CreateConnectionContext) createConnectionContext);
            PictogramElement element = addGraphicalRepresentation(createContext, graphElement);
            if (createConnectionContext != null && graphElement instanceof Node) {
                ((CreateConnectionContext) createConnectionContext).setTargetPictogramElement(element);
                ((CreateConnectionContext) createConnectionContext).setTargetAnchor(Graphiti.getPeService().getChopboxAnchor((AnchorContainer) element));
                CreateTransitionFeature createTransitionFeature = new CreateTransitionFeature();
                createTransitionFeature.setFeatureProvider(featureProvider);
                createTransitionFeature.create(createConnectionContext);
            }
        }
        alreadyAdded = true;
        if (viewer != null) {
            viewer.setCursor(null);
        }
        return null;
    }

    @Override
    public boolean canStartConnection(ICreateConnectionContext context) {
        return false;
    }

    protected ProcessDefinition getProcessDefinition() {
        return getFeatureProvider().getCurrentProcessDefinition();
    }

    private void setLocationAndSize(GraphElement element, CreateContext context, CreateConnectionContext connectionContext) {
        Dimension defaultSize = element.getTypeDefinition().getGraphitiEntry().getDefaultSize();
        if (context.getHeight() < defaultSize.height) {
            context.setHeight(defaultSize.height);
        }
        if (context.getWidth() < defaultSize.width) {
            context.setWidth(defaultSize.width);
        }
        if (connectionContext != null) {
            if (connectionContext.getTargetLocation() == null) {
                PictogramElement sourceElement = connectionContext.getSourcePictogramElement();
                GraphElement sourceGraphElement = (GraphElement) getBusinessObjectForPictogramElement(sourceElement);
                int shift = 5 * GRID_SIZE;
                if (sourceGraphElement instanceof IBoundaryEvent && !(sourceGraphElement.getParent() instanceof ProcessDefinition)) {
                    PictogramElement parentElement = Graphiti.getPeService().getPictogramElementParent(sourceElement);
                    int yBottom = parentElement.getGraphicsAlgorithm().getY() + parentElement.getGraphicsAlgorithm().getHeight();
                    int xDelta = (defaultSize.width - parentElement.getGraphicsAlgorithm().getWidth()) / 2;
                    GraphicsAlgorithm container = context.getTargetContainer().getGraphicsAlgorithm();
                    if (container.getHeight() < yBottom + shift + defaultSize.height) {
                        shift = 2 * GRID_SIZE;
                    }
                    context.setLocation(parentElement.getGraphicsAlgorithm().getX() - xDelta, yBottom + shift);
                } else {
                    GraphElement parent = (GraphElement) getBusinessObjectForPictogramElement(context.getTargetContainer());
                    List<GraphElement> childrenList = parent.getChildrenRecursive(GraphElement.class);
                    int xRight = sourceElement.getGraphicsAlgorithm().getX() + sourceElement.getGraphicsAlgorithm().getWidth();
                    int yDelta = (defaultSize.height - sourceElement.getGraphicsAlgorithm().getHeight()) / 2;
                    GraphicsAlgorithm container = context.getTargetContainer().getGraphicsAlgorithm();
                    if (container.getWidth() < xRight + shift + defaultSize.width) {
                        shift = 2 * GRID_SIZE;
                    }
                    int x = xRight + shift;
                    int y = sourceElement.getGraphicsAlgorithm().getY() - yDelta;
                    boolean available = false;
                    while (!available) {
                        available = true;
                        for (GraphElement children : childrenList) {
                            if (children.getConstraint() != null && children.getConstraint().x == x && children.getConstraint().y == y) {
                                y = y + children.getConstraint().height;
                                available = false;
                                break;
                            }
                        }
                    }
                    context.setLocation(x, y);
                }
            } else {
                context.setLocation(connectionContext.getTargetLocation().getX() - context.getWidth() / 2,
                        connectionContext.getTargetLocation().getY() - context.getHeight() / 2);
            }
        }
        element.setConstraint(new Rectangle(context.getX(), context.getY(), context.getWidth(), context.getHeight()));
    }

    private GraphElement parent;
    private Swimlane swimlane;
    private GraphElement graphElement;
    private Transition transition;

    @Override
    public void postUndo(IContext context) {
        if (graphElement instanceof Node) {
            transition = ((Node) graphElement).getArrivingTransitions().stream().findFirst().get();
            transition.getSource().removeLeavingTransition(transition);
        }
        parent.removeChild(graphElement);
        if (graphElement instanceof SwimlanedNode) {
            ((SwimlanedNode) graphElement).setSwimlane(null);
        }
        graphElement.setUiParentContainer(null);
        getDiagramBehavior().refresh();
    }

    @Override
    public boolean canRedo(IContext context) {
        return graphElement != null;
    }

    @Override
    public void postRedo(IContext context) {
        graphElement.setUiParentContainer(parent);
        parent.addChild(graphElement);
        if (graphElement instanceof SwimlanedNode) {
            ((SwimlanedNode) graphElement).setSwimlane(swimlane);
        }
        if (graphElement instanceof Node) {
            transition.getSource().addChild(transition);
        }
        getDiagramBehavior().refresh();
    }

    @Override
    public boolean canUndo(IContext context) {
        return graphElement != null;
    }

    @Override
    public boolean hasDoneChanges() {
        return graphElement != null;
    }
}
