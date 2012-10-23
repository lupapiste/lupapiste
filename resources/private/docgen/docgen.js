var docgen = (function() {
	
	function saveString(e) {
		info("saveString", typeof(e.target.value), e.target.value, e.target.getAttribute("data-path"));
	}
	
	function saveCheckbox(e) {
		info("saveCheckbox", e.target, e.target.getAttribute("data-path"));
	}
	
	function buildChoice(spec, model, path) {
		var label = document.createElement("label");
		label.setAttribute("class", "form_label");
		label.appendChild(document.createTextNode(spec.name));

		var choices = document.createElement("div");
		for (var i = 0; i < spec.body.length; i++) {
			choices.appendChild(build(spec.body[i], model, path));
		}
		
		var div = document.createElement("div");
		div.setAttribute("class", "form_field");
		div.appendChild(label);
		div.appendChild(choices);
		return div;
	}
	
	function buildCheckbox(spec, model, path) {
		var input = document.createElement("input");
		input.setAttribute("type", "checkbox");
		input.setAttribute("class", "form_input");
		input.setAttribute("data-path", path.concat([spec.name]));
		input.onchange = saveCheckbox;

		var label = document.createElement("label");
		label.setAttribute("class", "form_label");
		label.appendChild(document.createTextNode(spec.name));
		
		var span = document.createElement("span");
		span.setAttribute("class", "form_checkbox");
		span.appendChild(input);
		span.appendChild(label);
		return span;
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
		input.onchange = saveString;
		input.value = model[spec.name] || "";

		var div = document.createElement("div");
		div.setAttribute("class", "form_field");
		div.appendChild(label);
		div.appendChild(input);
		return div;
	}
	
	function buildUnknown(spec, model, path) {
		error("Unknown element type:", spec.type, path);
		return $("<h2>Unknown element type: " + spec.type + "</h2>");
	}
	
	var builders = {
		string: buildString,
		choice: buildChoice,
		checkbox: buildCheckbox,
		unknown: buildUnknown
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
