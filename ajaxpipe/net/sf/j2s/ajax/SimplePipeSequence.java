package net.sf.j2s.ajax;

import java.util.Map;

import net.sf.j2s.annotation.J2SIgnore;

public final class SimplePipeSequence extends SimpleSerializable {

	private static String[] mappings = new String[] {
			"sequence", "s"
	};
	
	public long sequence;

	@J2SIgnore
	private static Map<String, String> nameMappings = mappingFromArray(mappings, false);
	@J2SIgnore
	private static Map<String, String> aliasMappings = mappingFromArray(mappings, true);

	@J2SIgnore
	@Override
	protected Map<String, String> fieldNameMapping() {
		return nameMappings;
	}

	@J2SIgnore
	@Override
	protected Map<String, String> fieldAliasMapping() {
		return aliasMappings;
	}
	
	@Override
	protected String[] fieldMapping() {
		if (getSimpleVersion() >= 202) {
			return mappings;
		}
		return null;
	}
	
}
