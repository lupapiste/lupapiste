var docgen = (function() {
	
	function saveString(e) {
		info("saveString", "path:", e.target.getAttribute("data-path"), "value:", e.target.value);
	}
	
	function saveCheckbox(e) {
		info("saveCheckbox", "path:", e.target.getAttribute("data-path"), "value:", e.target.checked);
	}
	
	function makeLabel(text) {
		var label = document.createElement("label");
		label.className = "form_label";
		label.appendChild(document.createTextNode(text));
		return label;
	}
	
	function buildGroup(spec, model, path) {
		var name = spec.name;
		var choices = spec.body;
		var myModel = model[name] || {};
		var myPath = path.concat([name]);
		
		var choicesDiv = document.createElement("div");
		for (var i = 0; i < choices.length; i++) {
			choicesDiv.appendChild(build(choices[i], myModel, myPath));
		}
		
		var div = document.createElement("div");
		div.className = "form_field";
		div.appendChild(makeLabel(name));
		div.appendChild(choicesDiv);
		return div;
	}
	
	function buildCheckbox(spec, model, path) {
		var input = document.createElement("input");
		input.setAttribute("type", "checkbox");
		input.setAttribute("data-path", path.concat([spec.name]).join("."));
		if (model[spec.name]) input.setAttribute("checked", "checked");
		input.onchange = saveCheckbox;
		input.className = "form_input";

		var span = document.createElement("span");
		span.className = "form_field, form_checkbox";
		span.appendChild(input);
		span.appendChild(makeLabel(spec.name));
		return span;
	}
	
	function buildString(spec, model, path) {
		info("buildString", spec, model, path);
		
		var input = document.createElement("input");
		input.setAttribute("type", "text");
		input.setAttribute("data-path", path.concat([spec.name]).join("."));
		input.onchange = saveString;
		input.value = model[spec.name] || "";
		input.className = "form_input";

		var div = document.createElement("div");
		div.setAttribute("class", "form_field");
		div.appendChild(makeLabel(spec.name));
		div.appendChild(input);
		return div;
	}
	
	function buildUnknown(spec, model, path) {
		error("Unknown element type:", spec.type, path);
		return $("<h2>Unknown element type: " + spec.type + "</h2>");
	}
	
	var builders = {
		string: buildString,
		choice: buildGroup,
		checkbox: buildCheckbox,
		group: buildGroup,
		unknown: buildUnknown
	}
	
	function build(spec, model, path) {
		var builder = builders[spec.type] || buildUnknown;
		return builder(spec, model, path);
	}
	
	function appendElements(body, specs, model, path) {
		var l = specs.length;
		for (var i = 0; i < l; i++) {
			body.appendChild(build(specs[i], model, path));
		}
		return body;
	}

	function buildDocument(spec, model) {
		var section = document.createElement("section");
		section.className = "accordion";
		
		var title = document.createElement("h2");
		title.appendChild(document.createTextNode(spec.info.name));
		title.onclick = accordion.toggle;
		
		var elements = document.createElement("article");
		appendElements(elements, spec.body, model, []);
		
		section.appendChild(title);
		section.appendChild(elements);
		
		return section;
	}
	
	return {
		build: buildDocument
	};
	
})();
