var docgen = (function() {
	
	function loaderImg() {
		var img = document.createElement("img");
		img.src = "loader.gif";
		img.setAttribute("style", "width: 12px; height: 12px;");
		return img;
	}
	
	function save(e) {
		var target = e.target;
		var path = target.getAttribute("data-path");
		var value = (target.type === "checkbox") ? target.checked : target.value;
		
		info("saving", path, value);
		
		var label = document.getElementById(path.replace(/\./g, "-"));
		var loader = loaderImg();
		label.appendChild(loader);
		// Simulate lengthy ajax call...
		setTimeout(function() {
			info("saved", path, value);
			label.removeChild(loader);
			if (value === "err") {
				target.className = "form-input form-input-err";
			} else if (value === "warn") {
				target.className = "form-input form-input-warn";				
			} else {
				target.className = "form-input";
			}
		}, 1000);
		
		return false;
	}
	
	function makeLabel(type, path) {
		var label = document.createElement("label");
		label.id = path.replace(/\./g, "-");
		label.className = "form-label form-label-" + type;
		label.appendChild(document.createTextNode(loc(path)));
		return label;
	}

	function makeInput(type, path, value) {
		var input = document.createElement("input");
		input.setAttribute("data-path", path);
		input.type = type;
		input.className = "form-input form-" + type;
		input.onchange = save;
		if (type === "checkbox") {
			if (value) input.checked = "checked"; 
		} else {
			input.value = value || "";
		}
		return input;
	}
	
	function buildCheckbox(spec, model, path) {
		var myPath = path.concat([spec.name]).join(".");
		var span = document.createElement("span");
		span.className = "form-entry";
		span.appendChild(makeInput("checkbox", myPath, model[spec.name]));
		span.appendChild(makeLabel("checkbox", myPath));
		return span;
	}
	
	function buildString(spec, model, path) {
		var myPath = path.concat([spec.name]).join(".");
		var div = document.createElement("div");
		div.className = "form-entry";
		div.appendChild(makeLabel("text", myPath));
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
		div.className = "form-group";
		div.appendChild(makeLabel("group", myPath.join(".")));
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
