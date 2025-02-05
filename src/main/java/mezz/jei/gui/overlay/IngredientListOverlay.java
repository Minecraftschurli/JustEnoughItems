package mezz.jei.gui.overlay;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.runtime.IIngredientListOverlay;
import mezz.jei.config.IClientConfig;
import mezz.jei.config.IWorldConfig;
import mezz.jei.config.KeyBindings;
import mezz.jei.gui.GuiScreenHelper;
import mezz.jei.gui.elements.GuiIconToggleButton;
import mezz.jei.gui.ghost.GhostIngredientDragManager;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.gui.recipes.RecipesGui;
import mezz.jei.ingredients.IngredientManager;
import mezz.jei.input.GuiTextFieldFilter;
import mezz.jei.input.IClickedIngredient;
import mezz.jei.input.IMouseHandler;
import mezz.jei.input.IShowsRecipeFocuses;
import mezz.jei.input.click.MouseClickState;
import mezz.jei.util.CommandUtil;
import mezz.jei.util.MathUtil;
import mezz.jei.util.Rectangle2dBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.client.renderer.Rectangle2d;
import net.minecraft.client.util.InputMappings;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Tuple;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

public class IngredientListOverlay implements IIngredientListOverlay, IShowsRecipeFocuses {
	private static final int BORDER_PADDING = 2;
	private static final int BUTTON_SIZE = 20;
	private static final int SEARCH_HEIGHT = 20;

	private final GuiIconToggleButton configButton;
	private final IngredientGridWithNavigation contents;
	private final IClientConfig clientConfig;
	private final IWorldConfig worldConfig;
	private final GuiScreenHelper guiScreenHelper;
	private final GuiTextFieldFilter searchField;
	private final GhostIngredientDragManager ghostIngredientDragManager;
	private final IMouseHandler mouseHandler = new MouseHandler();
	private Rectangle2d displayArea = new Rectangle2d(0, 0, 0, 0);
	private boolean hasRoom;

	// properties of the gui we're next to
	@Nullable
	private IGuiProperties guiProperties;

	public IngredientListOverlay(
		IIngredientGridSource ingredientGridSource,
		IngredientManager ingredientManager,
		GuiScreenHelper guiScreenHelper,
		IngredientGridWithNavigation contents,
		IClientConfig clientConfig,
		IWorldConfig worldConfig
	) {
		this.guiScreenHelper = guiScreenHelper;
		this.contents = contents;
		this.clientConfig = clientConfig;
		this.worldConfig = worldConfig;
		ingredientGridSource.addListener(() -> onSetFilterText(worldConfig.getFilterText()));
		this.searchField = new GuiTextFieldFilter(ingredientGridSource, worldConfig);
		this.configButton = ConfigButton.create(this, worldConfig);
		this.ghostIngredientDragManager = new GhostIngredientDragManager(this.contents, guiScreenHelper, ingredientManager, worldConfig);
		this.clearKeyboardFocus();
	}

	public boolean isListDisplayed() {
		return worldConfig.isOverlayEnabled() && this.guiProperties != null && this.hasRoom;
	}

	private static Rectangle2d createDisplayArea(IGuiProperties guiProperties) {
		Rectangle2d screenRectangle = GuiProperties.getScreenRectangle(guiProperties);
		int guiRight = GuiProperties.getGuiRight(guiProperties);
		Tuple<Rectangle2d, Rectangle2d> result = MathUtil.splitX(screenRectangle, guiRight);
		Rectangle2d displayArea = result.getB();
		return new Rectangle2dBuilder(displayArea)
			.insetByPadding(BORDER_PADDING)
			.build();
	}

	public void updateScreen(@Nullable Screen guiScreen, boolean forceUpdate) {
		final boolean wasDisplayed = isListDisplayed();
		IGuiProperties guiProperties = guiScreenHelper.getGuiProperties(guiScreen);
		if (guiProperties == null) {
			if (this.guiProperties != null) {
				this.guiProperties = null;
				clearKeyboardFocus();
				this.ghostIngredientDragManager.stopDrag();
			}
		} else if (forceUpdate || this.guiProperties == null || !GuiProperties.areEqual(this.guiProperties, guiProperties)) {
			updateNewScreen(guiProperties);
		}

		if (wasDisplayed && !isListDisplayed()) {
			worldConfig.saveFilterText();
		}
	}

	private void updateNewScreen(IGuiProperties guiProperties) {
		this.guiProperties = guiProperties;
		this.displayArea = createDisplayArea(guiProperties);

		final boolean searchBarCentered = isSearchBarCentered(this.clientConfig, guiProperties);

		Set<Rectangle2d> guiExclusionAreas = guiScreenHelper.getGuiExclusionAreas();
		Rectangle2d availableContentsArea = new Rectangle2dBuilder(this.displayArea)
			.subtractHeight(searchBarCentered ? 0 : SEARCH_HEIGHT + BORDER_PADDING)
			.build();
		this.hasRoom = this.contents.updateBounds(availableContentsArea, guiExclusionAreas);

		// update area to match contents size
		Rectangle2d contentsArea = this.contents.getArea();
		this.displayArea = new Rectangle2dBuilder(this.displayArea)
			.setX(contentsArea)
			.setWidth(contentsArea)
			.build();

		Tuple<Rectangle2d, Rectangle2d> result = getSearchAndConfigArea(searchBarCentered, guiProperties, this.displayArea);
		Rectangle2d searchAndConfigArea = result.getB();

		result = MathUtil.splitXRight(searchAndConfigArea, BUTTON_SIZE);
		Rectangle2d searchArea = result.getA();
		Rectangle2d configButtonArea = result.getB();

		this.searchField.updateBounds(searchArea);
		this.configButton.updateBounds(configButtonArea);

		updateLayout(false);
	}

	private static boolean isSearchBarCentered(IClientConfig clientConfig, IGuiProperties guiProperties) {
		return clientConfig.isCenterSearchBarEnabled() &&
			GuiProperties.getGuiBottom(guiProperties) + SEARCH_HEIGHT < guiProperties.getScreenHeight();
	}

	private static Tuple<Rectangle2d, Rectangle2d> getSearchAndConfigArea(boolean searchBarCentered, IGuiProperties guiProperties, Rectangle2d displayArea) {
		if (searchBarCentered) {
			Rectangle2d guiRectangle = GuiProperties.getGuiRectangle(guiProperties);
			Rectangle2d searchRect = new Rectangle2d(
				guiRectangle.getX(),
				displayArea.getHeight() - SEARCH_HEIGHT,
				guiRectangle.getWidth(),
				SEARCH_HEIGHT
			);
			return new Tuple<>(displayArea, searchRect);
		} else {
			return MathUtil.splitYBottom(displayArea, SEARCH_HEIGHT);
		}
	}

	public void updateLayout(boolean filterChanged) {
		this.contents.updateLayout(filterChanged);
		this.searchField.update();
	}

	@SuppressWarnings("deprecation")
	public void drawScreen(Minecraft minecraft, MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
		if (isListDisplayed()) {
			RenderSystem.disableLighting();
			this.searchField.renderButton(matrixStack, mouseX, mouseY, partialTicks);
			this.contents.draw(minecraft, matrixStack, mouseX, mouseY, partialTicks);
		}
		if (this.guiProperties != null) {
			this.configButton.draw(matrixStack, mouseX, mouseY, partialTicks);
		}
	}

	public void drawTooltips(Minecraft minecraft, MatrixStack matrixStack, int mouseX, int mouseY) {
		if (isListDisplayed()) {
			this.ghostIngredientDragManager.drawTooltips(minecraft, matrixStack, mouseX, mouseY);
			this.contents.drawTooltips(minecraft, matrixStack, mouseX, mouseY);
		}
		if (this.guiProperties != null) {
			this.configButton.drawTooltips(matrixStack, mouseX, mouseY);
		}
	}

	public void drawOnForeground(Minecraft minecraft, MatrixStack matrixStack, ContainerScreen<?> gui, int mouseX, int mouseY) {
		if (isListDisplayed()) {
			matrixStack.pushPose();
			matrixStack.translate(-gui.getGuiLeft(), -gui.getGuiTop(), 0);
			this.ghostIngredientDragManager.drawOnForeground(minecraft, matrixStack, mouseX, mouseY);
			matrixStack.popPose();
		}
	}

	public void handleTick() {
		if (this.isListDisplayed()) {
			this.searchField.tick();
		}
	}

	public boolean isMouseOver(double mouseX, double mouseY) {
		if (isListDisplayed()) {
			if (this.clientConfig.isCenterSearchBarEnabled() && searchField.isMouseOver(mouseX, mouseY)) {
				return true;
			}
			return MathUtil.contains(displayArea, mouseX, mouseY) &&
				!guiScreenHelper.isInGuiExclusionArea(mouseX, mouseY);
		}
		if (this.guiProperties != null) {
			return this.configButton.isMouseOver(mouseX, mouseY);
		}
		return false;
	}

	@Override
	@Nullable
	public IClickedIngredient<?> getIngredientUnderMouse(double mouseX, double mouseY) {
		if (isListDisplayed()) {
			return this.contents.getIngredientUnderMouse(mouseX, mouseY);
		}
		return null;
	}

	@Override
	public boolean canSetFocusWithMouse() {
		return this.isListDisplayed() && this.contents.canSetFocusWithMouse();
	}

	private ClickResult handleMouseClickedInternal(Screen screen, double mouseX, double mouseY, int mouseButton, MouseClickState clickState) {
		if (isListDisplayed()) {
			if (this.ghostIngredientDragManager.handleMouseClicked(screen, mouseX, mouseY, mouseButton, clickState)) {
				return new ClickResult(true, false);
			}
		}

		if (this.guiProperties != null) {
			IMouseHandler mouseHandler = this.configButton.getMouseHandler();
			IMouseHandler mouseHandled = mouseHandler.handleClick(screen, mouseX, mouseY, mouseButton, clickState);
			if (mouseHandled != null) {
				return new ClickResult(true, false);
			}
		}

		if (isListDisplayed()) {
			if (this.contents.getMouseHandler().handleClick(screen, mouseX, mouseY, mouseButton, clickState) != null) {
				return new ClickResult(true, false);
			}

			if (handleSearchClick(screen, mouseX, mouseY, mouseButton, clickState)) {
				return new ClickResult(true, true);
			}

			if (handleCheatClick(this, mouseX, mouseY, mouseButton, worldConfig, clientConfig, clickState)) {
				return new ClickResult(true, false);
			}
		}
		return new ClickResult(false, false);
	}

	private boolean handleSearchClick(Screen screen, double mouseX, double mouseY, int mouseButton, MouseClickState clickState) {
		IMouseHandler mouseHandler = this.searchField.getMouseHandler();
		IMouseHandler handled = mouseHandler.handleClick(screen, mouseX, mouseY, mouseButton, clickState);
		if (handled != null) {
			if (!clickState.isSimulate()) {
				updateLayout(true);
			}
			return true;
		}
		return false;
	}

	private static boolean handleCheatClick(
		IShowsRecipeFocuses showsRecipeFocuses,
		double mouseX,
		double mouseY,
		int mouseButton,
		IWorldConfig worldConfig,
		IClientConfig clientConfig,
		MouseClickState clickState
	) {
		if (!worldConfig.isCheatItemsEnabled()) {
			return false;
		}

		Minecraft minecraft = Minecraft.getInstance();
		Screen currentScreen = minecraft.screen;
		if (currentScreen == null || currentScreen instanceof RecipesGui) {
			return false;
		}

		InputMappings.Input input = InputMappings.Type.MOUSE.getOrCreate(mouseButton);
		if (mouseButton != 0 && mouseButton != 1 && !minecraft.options.keyPickItem.isActiveAndMatches(input)) {
			return false;
		}

		IClickedIngredient<?> clicked = showsRecipeFocuses.getIngredientUnderMouse(mouseX, mouseY);
		if (clicked == null) {
			return false;
		}

		if (!clickState.isSimulate()) {
			ItemStack itemStack = clicked.getCheatItemStack();
			if (!itemStack.isEmpty()) {
				CommandUtil.giveStack(itemStack, input, clientConfig);
			}
		}
		return true;
	}

	public IMouseHandler getMouseHandler() {
		return mouseHandler;
	}

	@Override
	public boolean hasKeyboardFocus() {
		return isListDisplayed() && this.searchField.isFocused();
	}

	public void clearKeyboardFocus() {
		setKeyboardFocus(false);
	}

	private void setKeyboardFocus(boolean keyboardFocus) {
		this.searchField.setFocused(keyboardFocus);
	}

	public boolean onGlobalKeyPressed(InputMappings.Input input, MouseClickState clickState) {
		if (isListDisplayed()) {
			if (KeyBindings.toggleCheatMode.isActiveAndMatches(input)) {
				if (!clickState.isSimulate()) {
					worldConfig.toggleCheatItemsEnabled();
				}
				return true;
			}
			if (KeyBindings.toggleEditMode.isActiveAndMatches(input)) {
				if (!clickState.isSimulate()) {
					worldConfig.toggleEditModeEnabled();
				}
				return true;
			}
			if (KeyBindings.focusSearch.isActiveAndMatches(input)) {
				if (!clickState.isSimulate()) {
					setKeyboardFocus(true);
				}
				return true;
			}
		}
		return false;
	}

	public boolean onCharTyped(char codePoint, int modifiers) {
		if (isListDisplayed() &&
			hasKeyboardFocus() &&
			searchField.charTyped(codePoint, modifiers)) {
			boolean changed = worldConfig.setFilterText(searchField.getValue());
			if (changed) {
				updateLayout(true);
			}
			return true;
		}
		return false;
	}

	public boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
		if (isListDisplayed()) {
			if (hasKeyboardFocus() &&
				searchField.keyPressed(keyCode, scanCode, modifiers)) {
				boolean changed = worldConfig.setFilterText(searchField.getValue());
				if (changed) {
					updateLayout(true);
				}
				return true;
			}
			boolean pressed = this.contents.onKeyPressed(keyCode, scanCode, modifiers);
			if (pressed) {
				clearKeyboardFocus();
			}
			return pressed;
		}
		return false;
	}

	@Nullable
	@Override
	public Object getIngredientUnderMouse() {
		if (isListDisplayed()) {
			IIngredientListElement<?> elementUnderMouse = this.contents.getElementUnderMouse();
			if (elementUnderMouse != null) {
				return elementUnderMouse.getIngredient();
			}
		}
		return null;
	}

	@Nullable
	@Override
	public <T> T getIngredientUnderMouse(IIngredientType<T> ingredientType) {
		Object ingredient = getIngredientUnderMouse();
		Class<? extends T> ingredientClass = ingredientType.getIngredientClass();
		if (ingredientClass.isInstance(ingredient)) {
			return ingredientClass.cast(ingredient);
		}
		return null;
	}

	public void onSetFilterText(String filterText) {
		this.searchField.setValue(filterText);
		updateLayout(true);
	}

	@SuppressWarnings("UnstableApiUsage")
	@Override
	public ImmutableList<Object> getVisibleIngredients() {
		if (isListDisplayed()) {
			List<IIngredientListElement<?>> visibleElements = this.contents.getVisibleElements();
			return visibleElements.stream()
				.map(IIngredientListElement::getIngredient)
				.collect(ImmutableList.toImmutableList());
		}
		return ImmutableList.of();
	}

	private static class ClickResult {
		public final boolean handled;
		public final boolean keyboardFocused;

		public ClickResult(boolean handled, boolean keyboardFocused) {
			this.handled = handled;
			this.keyboardFocused = keyboardFocused;
		}
	}

	private class MouseHandler implements IMouseHandler {
		@Nullable
		@Override
		public IMouseHandler handleClick(Screen screen, double mouseX, double mouseY, int mouseButton, MouseClickState clickState) {
			ClickResult clickResult = handleMouseClickedInternal(screen, mouseX, mouseY, mouseButton, clickState);
			if (!clickState.isSimulate()) {
				setKeyboardFocus(clickResult.keyboardFocused);
			}
			if (!clickResult.handled) {
				return null;
			}
			return this;
		}

		@Override
		public void handleMouseClickedOut(int mouseButton) {
			clearKeyboardFocus();
		}

		@Override
		public boolean handleMouseScrolled(double mouseX, double mouseY, double scrollDelta) {
			if (!isMouseOver(mouseX, mouseY)) {
				return false;
			}
			IMouseHandler mouseHandler = contents.getMouseHandler();
			return mouseHandler.handleMouseScrolled(mouseX, mouseY, scrollDelta);
		}
	}
}
