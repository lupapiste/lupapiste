var docgen = (function() {
	
	function save(path, value) {
		info("save", path, value);
	}
	
	function saveValue(e) {
		var target = e.target;
		save(target.getAttribute("data-path"), target.value);
	}
	
	function saveCheckbox(e) {
		var target = e.target;
		save(target.getAttribute("data-path"), target.checked);
	}
	
	function makeLabel(text, path) {
		var label = document.createElement("label");
		label.className = "form-label";
		label.appendChild(document.createTextNode(loc(text)));
		return label;
	}

	function makeInput(type, path, value) {
		var input = document.createElement("input");
		input.setAttribute("data-path", path);
		input.type = type;
		input.className = "form-input form-" + type;
		if (type === "checkbox") {
			input.onchange = saveCheckbox;
			if (value) input.checked = "checked"; 
		} else {
			input.onchange = saveValue;
			input.value = value || "";
		}
		return input;
	}
	
	function buildCheckbox(spec, model, path) {
		var myPath = path.concat([spec.name]).join(".");

		var span = document.createElement("span");
		span.className = "form-entry";
		span.appendChild(makeInput("checkbox", myPath, model[spec.name]));
		span.appendChild(makeLabel(myPath));
		return span;
	}
	
	function buildString(spec, model, path) {
		var myPath = path.concat([spec.name]).join(".");

		var div = document.createElement("div");
		div.className = "form-entry";
		div.appendChild(makeLabel(myPath));
		div.appendChild(makeInput("text", myPath, model[spec.name]));
		return div;
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
		div.className = "form-field";
		div.appendChild(makeLabel(myPath.join(".")));
		div.appendChild(choicesDiv);
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
	};
	
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
		title.appendChild(document.createTextNode(loc(spec.info.name)));
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
