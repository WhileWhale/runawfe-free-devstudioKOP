package ru.runa.gpd.quick.tag;

import java.time.LocalDate;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import ru.runa.wfe.var.dto.WfVariable;
import ru.runa.wfe.var.format.ListFormat;
import freemarker.template.TemplateModelException;

public class InputDateVariableTag extends FreemarkerTagGpdWrap{
    private static final long serialVersionUID = 1L;
    private static final Log log = LogFactory.getLog(InputVariableTag.class);

    @Override
    protected Object executeTag() throws TemplateModelException {
        String variableName = getParameterAs(String.class, 0);
        WfVariable variable = variableProvider.getVariableNotNull(variableName);
        String formatClassName = variable.getDefinition().getFormatClassName();
        Object value = variableProvider.getValue(variableName);
        String html;
        LocalDate date = LocalDate.now();
        String valueAndDate = (String)value + date;
        if (ListFormat.class.getName().equals(formatClassName)) {
            EditListTag tag = new EditListTag();
            //tag.initChained(this);
            html = tag.renderRequest();
        } else {
            html = ViewUtil.getComponentInput(user, variableName, formatClassName, valueAndDate);
        }
        if (html.length() == 0) {
            log.warn("No HTML built for " + variable);
        }
        return html;
    }

}