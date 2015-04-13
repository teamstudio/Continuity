package com.teamstudio.renderers;

import java.io.IOException;
import java.io.Writer;

import javax.faces.component.UIComponent;
import javax.faces.component.UISelectOne;
import javax.faces.context.FacesContext;
import javax.faces.render.Renderer;

public class SelectOne extends Renderer {
	public void encodeBegin(FacesContext context, UIComponent component) throws IOException {
        UISelectOne obj = (UISelectOne)component;
        Writer writer = context.getResponseWriter();
        writer.write(obj.getValue().toString());
    }

    public void encodeEnd(FacesContext context, UIComponent component) throws IOException {
    }

}
