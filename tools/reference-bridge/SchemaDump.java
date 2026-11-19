// Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
// Drishti Rachana. Proprietary: see LICENSE.
// Writes the reference's ViewModel schema (ViewModelSchema.build()) as JSON on standard output, for the comparison of plan H4.
// Run: java -cp <the class path of RefBridge plus drishti-engine> tools/reference-bridge/SchemaDump.java > viewmodel.reference.json
import com.fasterxml.jackson.databind.ObjectMapper;

public class SchemaDump {
    public static void main(String[] a) throws Exception {
        System.out.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(com.ash.drishti.engine.view.ViewModelSchema.build()));
    }
}
