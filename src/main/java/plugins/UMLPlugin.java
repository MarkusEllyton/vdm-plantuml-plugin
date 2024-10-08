package plugins;

import java.io.BufferedWriter;
import java.io.File;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.fujitsu.vdmj.lex.Dialect;
import com.fujitsu.vdmj.tc.definitions.TCClassDefinition;
import com.fujitsu.vdmj.tc.definitions.TCClassList;

import json.JSONArray;
import json.JSONObject;
import lsp.Utils;
import plugins.UML2VDM.Uml2vdmMain;
import plugins.VDM2UML.PlantBuilder;
import plugins.VDM2UML.UMLGenerator;
import rpc.RPCErrors;
import rpc.RPCMessageList;
import rpc.RPCRequest;
import workspace.Diag;
import workspace.EventHub;
import workspace.EventListener;
import workspace.PluginRegistry;
import workspace.events.LSPEvent;
import workspace.events.UnknownTranslationEvent;
import workspace.plugins.AnalysisPlugin;
import workspace.plugins.TCPlugin;

public class UMLPlugin extends AnalysisPlugin implements EventListener {

    public static UMLPlugin factory(Dialect dialect) throws Exception {
        switch (dialect) {
            case VDM_RT:
                return new UMLPlugin();
            case VDM_PP:
                return new UMLPlugin();

            case VDM_SL:
            default:
                throw new Exception("Unknown dialect: " + dialect);
        }
    }

    @Override
    public String getName() {
        return "UML";
    }

    @Override
    public void init() {
        EventHub.getInstance().register(UnknownTranslationEvent.class, this);
    }

    @Override
    public RPCMessageList handleEvent(LSPEvent event) throws Exception {

        if (event instanceof UnknownTranslationEvent) {
            UnknownTranslationEvent ute = (UnknownTranslationEvent) event;

            if (ute.languageId.equals("uml2vdm")) {
                return analyseUML2VDM(event.request);
            } else if (ute.languageId.equals("vdm2uml")) {
                return analyseVDM2UML(event.request);
            }
        }

        return null; // Not handled
    }

    public RPCMessageList analyseUML2VDM(RPCRequest request) {
        try {
            JSONObject params = request.get("params");

            File uri = Utils.uriToFile(params.get("uri"));
            File saveUri = Utils.uriToFile(params.get("saveUri"));
            Uml2vdmMain puml = new Uml2vdmMain(uri, saveUri);
            puml.run();

            return new RPCMessageList(request, new JSONObject("uri", saveUri.toURI().toString()));
        } catch (Exception e) {
            Diag.error(e);
            return new RPCMessageList(request, RPCErrors.InternalError, e.getMessage());
        }
    }

    public RPCMessageList analyseVDM2UML(RPCRequest request) {
        boolean isProject = false;
        try {
            JSONObject params = request.get("params");
            File saveUri = Utils.uriToFile(params.get("saveUri"));
            URI uri = URI.create(params.get("uri"));

            if (uri != null) {
                isProject = Files.isDirectory(Paths.get(uri));
            }

            TCPlugin tcPlugin = PluginRegistry.getInstance().getPlugin("TC");
            TCClassList classes = tcPlugin.getTC();

            if (classes == null || classes.isEmpty()) {
                return new RPCMessageList(request, RPCErrors.InvalidRequest, "No classes were found");
            }

            PlantBuilder pBuilder = new PlantBuilder(classes);
            String fileName = "";
            String name;

            if (isProject) {
                String projectName = Paths.get(uri).getFileName().toString();
                name = projectName;
                fileName = projectName;
                for (TCClassDefinition cdef : classes) {
                    cdef.apply(new UMLGenerator(), pBuilder);
                }
            } else {
                String className = Paths.get(uri).getFileName().toString();
                className = className.substring(0, className.lastIndexOf('.'));
                fileName = className;
                name = fileName;
                for (TCClassDefinition cdef : classes) {
                    String cdefName = cdef.toString();
                    cdefName = cdefName.substring(cdefName.indexOf(" ") + 1, cdefName.indexOf("\n"));
                    if (cdefName.equalsIgnoreCase(className)) {
                        name = cdefName;
                        cdef.apply(new UMLGenerator(), pBuilder);
                    }
                }
            }

            StringBuilder boiler = UMLGenerator.buildBoiler(name);

            File outfile = new File(saveUri, fileName + ".puml");
            PrintWriter out = new PrintWriter(outfile);
            try (BufferedWriter writer = new BufferedWriter(out)) {
                writer.append(boiler);
                writer.append(pBuilder.defs);
                writer.append(pBuilder.asocs);
                writer.append("\n");
                writer.append("@enduml");
            }
            out.close();

            return new RPCMessageList(request, new JSONObject("uri", saveUri.toURI().toString()));
        } catch (Exception e) {
            Diag.error(e);
            return new RPCMessageList(request, RPCErrors.InternalError, e.getMessage());
        }
    }

    @Override
    public void setServerCapabilities(JSONObject capabilities) {
        JSONObject experimental = capabilities.get("experimental");

        if (experimental != null) {
            JSONObject provider = experimental.get("translateProvider");

            if (provider != null) {
                JSONArray ids = provider.get("languageId");

                if (ids != null) {
                    ids.add("vdm2uml");
                    ids.add("uml2vdm");
                }
            }
        }
    }
}
