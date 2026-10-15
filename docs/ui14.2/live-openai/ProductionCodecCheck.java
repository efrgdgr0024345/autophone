import com.blackcat.remote.*;
import org.json.*;
import java.nio.file.*;
import java.util.*;
public class ProductionCodecCheck {
 static final Path ROOT=Path.of("/workspace/autophone");
 static final Path OUT=Path.of("/workspace/security-review/ui14.2/live-openai");
 static final String TARGET="Ubuntu 24.04 LTS, bash, disposable Linux demo; local console";
 static String read(Path p)throws Exception{return Files.readString(p);}
 static void save(String n,JSONObject o)throws Exception{Files.writeString(OUT.resolve(n+".request.json"),o.toString(2));}
 static void need(boolean b,String why){if(!b)throw new AssertionError(why);}
 static JSONObject fixture(String id)throws Exception{return new JSONObject(read(ROOT.resolve("docs/ui14.1/image-loop/request-"+id+".json")));}
 public static void main(String[] args)throws Exception {
  if(args[0].equals("export")){
   save("plan",PlanCodec.INSTANCE.request("Create a detailed phased plan to lock down my Ubuntu 24.04 LTS computer from attackers. I use a local console, have recovery media and backups, and need SSH access preserved. Begin with a comprehensive read-only discovery phase (at least six individual useful commands) and include the full security roadmap, safeguards, later verification and recovery. Do not propose changes until observations are available. This is planning only; do not claim anything ran.",TARGET,PlanCodec.DEFAULT_MODEL));
   save("revision",PlanCodec.INSTANCE.request(fixture("009").getString("context"),TARGET,PlanCodec.DEFAULT_MODEL));
   for(String[] item:new String[][]{{"preflight","001"},{"clipped","003"},{"page","004"}}){
    JSONObject f=fixture(item[1]);
    SuggestedCommand step=new SuggestedCommand("Synthetic terminal check",f.getString("command"),"Interpret only visible diagnostic evidence.","low",false,"Find why the demo service failed.",List.of());
    save(item[0],AutoObservationCodec.INSTANCE.request(f.getString("goal"),step,TARGET,PlanCodec.DEFAULT_MODEL,Files.readAllBytes(ROOT.resolve("docs/ui14.1/image-loop/photo-"+item[1]+".jpg")),f.getBoolean("preflight"),f.getString("collection_context")));
   }
   save("speech",OpenAiSpeechCodec.INSTANCE.request("First, I'll check which services are listening. This only reads the current settings. Then I'll review the result before choosing the next step. You can stop me at any time."));
   System.out.println("Exported six requests through unchanged production codecs.");return;
  }
  String name=args[0];String raw=read(OUT.resolve(name+".response.json"));JSONObject result=new JSONObject().put("scenario",name);
  if(name.equals("plan")||name.equals("revision")){
   CommandPlan p=PlanCodec.INSTANCE.parseResponse(raw);
   need(p.getQuestions().isEmpty(),"Unexpected blocking clarification");
   need(p.getCommands().size()>=(name.equals("plan")?6:1),"Insufficient command list");
   need(p.getRoadmap().size()>=(name.equals("plan")?8:1),"Missing detailed roadmap");
   for(SuggestedCommand s:p.getCommands())need(AutoRunPolicy.INSTANCE.canRunReviewed(s),"Command is not accepted by production Auto policy: "+s.getCommand());
   if(name.equals("revision"))need(p.getCommands().stream().anyMatch(s->s.getCommand().equals("cat /etc/os-release")),"Missing evidence-driven OS diagnostic");
   result.put("commands",p.getCommands().size()).put("roadmap_phases",p.getRoadmap().size()).put("production_auto_policy",true).put("mixed_questions_and_commands",false);
  }else{
   AutoObservation o=AutoObservationCodec.INSTANCE.parseResponse(raw);
   if(name.equals("preflight"))need(o.allowsNext(""),"Idle prompt not accepted by production preflight");
   else {
    JSONObject f=fixture(name.equals("clipped")?"003":"004");String marker=new JSONObject(f.getString("collection_context")).getString("expected_marker");
    need(o.getPageMarker().equals(marker),"Incorrect image marker");need(o.getTerminalState().equals("shell_prompt"),"Fresh prompt not seen");need(o.getExitCode().equals("0"),"Wrong visible exit status");
    need(o.getPageState().equals(name.equals("clipped")?"unreadable":"more"),"Incorrect page completeness decision");
    if(name.equals("page")){for(String line:List.of("01 demo-service: starting read-only compatibility check","02 host configuration loaded","03 data directory readable","04 port check completed"))need(o.getPageText().contains(line),"Missing visible page text");}
   }
   result.put("terminal_state",o.getTerminalState()).put("outcome",o.getOutcome()).put("page_state",o.getPageState()).put("revision_needed",o.getRevisionNeeded());
  }
  result.put("passed",true);Files.writeString(OUT.resolve(name+".parsed.json"),result.toString(2));System.out.println(result);
 }
}
