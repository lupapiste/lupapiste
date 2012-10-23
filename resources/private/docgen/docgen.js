var docgen = (function() {
	
	function buildGroup(spec, model, path) {
		
	}
	
	function blur(e) {
		console.log("blur", typeof(e.target.value), e.target.value, e.target.getAttribute("data-path"));
	}
	
	function buildString(spec, model, path) {
		info("buildString", spec, model, path);
		
		var label = document.createElement("label");
		label.setAttribute("class", "form_label");
		label.appendChild(document.createTextNode(spec.name));
		
		var input = document.createElement("input");
		input.setAttribute("class", "form_input");
		input.setAttribute("type", "text");
		input.setAttribute("data-path", path.concat([spec.name]));
		
		var value = model[spec.name];
		if (value) input.value = value;
		
		input.onblur = blur;
		
		var div = document.createElement("div");
		div.setAttribute("class", "form_field");
		div.appendChild(label);
		div.appendChild(input);
		div.appendChild(document.createElement("br"));
		
		return div;
	}
	
	function buildUnknown(spec, model, path) {
		error("Unknown element type:", spec.type, path);
		return $("<h2>Unknown element type: " + spec.type + "</h2>");
	}
	
	var builders = {
		group: buildGroup,
		string: buildString
	}
	
	function build(spec, model, path) {
		var builder = builders[spec.type] || buildUnknown;
		return builder(spec, model, path);
	}
	
	function appendElements(body, specs, model, path) {
		var l = specs.length;
		for (var i = 0; i < l; i++) {
			body.append(build(specs[i], model, path));
		}
		return body;
	}

	function buildDocument(spec, model) {
		return $("<section class='accordion'></section>")
			.append($("<h2>" + spec.info.name + "</h2>").click(accordion.toggle))
			.append(appendElements($("<article></article>"), spec.body, model, []));
	}
	
	return {
		build: buildDocument
	};
	
})();
