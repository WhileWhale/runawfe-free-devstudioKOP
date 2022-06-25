package ru.runa.gpd.quick.tag;

import java.time.LocalDate;
import ru.runa.wfe.var.dto.WfVariable;
import freemarker.template.TemplateModelException;

public class DisplayDateVariableTag extends FreemarkerTagGpdWrap {
    private static final long serialVersionUID = 1L;

    @Override
    protected Object executeTag() throws TemplateModelException {
        String variableName = getParameterAs(String.class, 0);
        boolean componentView = getParameterAs(boolean.class, 1);
        WfVariable variable = variableProvider.getVariableNotNull(variableName);
        LocalDate date = LocalDate.now();
        if (componentView) {
            return ViewUtil.getComponentOutput(user, variableName, variable.getDefinition().getFormatClassName(), variable.getValue());
        } else {
            String html = "<span class=\"displayVariable\">";
            html += ViewUtil.getOutput(user, webHelper, variableProvider.getProcessId(), variable);
            html += date;
            html += "</span>";
            return html;
        }
    }
}
