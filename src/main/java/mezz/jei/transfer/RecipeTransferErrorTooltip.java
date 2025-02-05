package mezz.jei.transfer;

import com.mojang.blaze3d.matrix.MatrixStack;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;

import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.config.Constants;
import mezz.jei.gui.TooltipRenderer;
import net.minecraft.util.text.TranslationTextComponent;

public class RecipeTransferErrorTooltip implements IRecipeTransferError {
	private final List<ITextComponent> message = new ArrayList<>();

	public RecipeTransferErrorTooltip(ITextComponent message) {
		this.message.add(new TranslationTextComponent("jei.tooltip.transfer"));
		IFormattableTextComponent messageTextComponent = message.copy();
		this.message.add(messageTextComponent.withStyle(TextFormatting.RED));
	}

	@Override
	public Type getType() {
		return Type.USER_FACING;
	}

	@Override
	public void showError(MatrixStack matrixStack, int mouseX, int mouseY, IRecipeLayout recipeLayout, int recipeX, int recipeY) {
		TooltipRenderer.drawHoveringText(message, mouseX, mouseY, Constants.MAX_TOOLTIP_WIDTH, matrixStack);
	}
}
