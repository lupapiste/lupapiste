var docgen = (function() {
	
	function loaderImg() {
		var img = document.createElement("img");
		img.src = "loader.gif";
		return img;
	}
	
	function save(e) {
		var target = e.target;
		var path = target.name;
		var value = (target.type === "checkbox") ? target.checked : target.value;
		
		var loader = loaderImg();
		var label = document.getElementById(path.replace(/\./g, "-"));
		label.appendChild(loader);
		
		console.log("save", path, value);
		setTimeout(function() {
			target.className = "form-input";
			label.removeChild(loader);
		}, 1000);

		/*
		ajax
			.query("update-doc", {doc: "123", updates: [[path, value]]})
			.success(function() {
				target.className = "form-input";
			})
			.error(function(e) {
				var status = e["status"] || "err";
				target.className = "form-input form-input-" + status;
			})
			.fail(function() {
				target.className = "form-input form-input-err";
			})
			.complete(function() {
				label.removeChild(loader);
			})
			.call();
		*/
		
		e.preventDefault();
		return false;
	}
	
	function makeLabel(type, path) {
		var label = document.createElement("label");
		label.id = path.replace(/\./g, "-");
		label.className = "form-label form-label-" + type;
		label.appendChild(document.createTextNode(loc(path)));
		return label;
	}

	function makeInput(type, path, value, extraClass) {
		var input = document.createElement("input");
		input.name = path;
		input.type = type;
		input.className = "form-input form-" + type + " " + (extraClass || "");
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
	
	function buildString(spec, model, path, partOfChoice) {
		var myPath = path.concat([spec.name]).join(".");
		var div = document.createElement("span");
		var sizeClass = "";
		if (spec.size) {
			if (spec.size === "s") sizeClass = "form-string-small";
			if (spec.size === "l") sizeClass = "form-string-large";
		}
		div.className = "form-entry";
		div.appendChild(makeLabel(partOfChoice ? "string-choice" : "string", myPath));
		div.appendChild(makeInput("string", myPath, model[spec.name], sizeClass));
		if (spec.unit) {
			var unit = document.createElement("span");
			unit.className = "form-string-unit";
			unit.appendChild(document.createTextNode(loc(spec.unit)));
			div.appendChild(unit);
		}
		return div;
	}
	
	function buildText(spec, model, path) {
		var myPath = path.concat([spec.name]).join(".");

		var input = document.createElement("textarea");
		input.name = myPath;
		input.setAttribute("rows", spec["rows"] || "10");
		input.setAttribute("cols", spec["cols"] || "40");
		input.className = "form-input form-text";
		input.onchange = save;
		input.value = model[spec.name] || "";

		var div = document.createElement("span");
		div.className = "form-entry";
		div.appendChild(makeLabel("text", myPath));
		div.appendChild(input);
		return div;
	}
	
	function buildDate(spec, model, path) {
		var myPath = path.concat([spec.name]).join(".");

		var input = document.createElement("input");
		input.name = myPath;
		input.type = "date";
		input.className = "form-input form-date";
		input.onchange = save;
		input.value = model[spec.name] || "";

		var div = document.createElement("span");
		div.className = "form-entry";
		div.appendChild(makeLabel("date", myPath));
		div.appendChild(input);
		return div;
	}
	
	function buildSelect(spec, model, path) {
		var myPath = path.concat([spec.name]).join(".");

		var select = document.createElement("select");
		select.name = myPath;
		select.className = "form-input form-select";
		select.onchange = save;
		
		var selectedOption = Object.keys(model[spec.name])[0];
		console.log("selectedOption", selectedOption, spec.name);
		
		var option = document.createElement("option");
		option.value = "";
		option.appendChild(document.createTextNode(loc("selectone")));
		if (selectedOption === "") option.selected = "selected";
		select.appendChild(option);
		
		$.each(spec.body, function(i, o) {
			var name = o.name;
			var option = document.createElement("option");
			option.value = name;
			option.appendChild(document.createTextNode(loc(name)));
			if (selectedOption === name) option.selected = "selected";
			select.appendChild(option);
		});

		var div = document.createElement("span");
		div.className = "form-entry";
		div.appendChild(makeLabel("select", myPath));
		div.appendChild(select);
		return div;
	}

	/*
	<select name="cars">
	<option value="volvo">Volvo</option>
	<option value="saab">Saab</option>
	<option value="fiat">Fiat</option>
	<option value="audi">Audi</option>
	</select>
	*/
	
	function buildChoice(spec, model, path) {
		var name = spec.name;
		var choices = spec.body;
		var myModel = model[name] || {};
		var myPath = path.concat([name]);
		
		var choicesDiv = document.createElement("div");
		for (var i = 0; i < choices.length; i++) {
			var choice = choices[i];
			if (choice.type === "string") {
				choicesDiv.appendChild(buildString(choice, myModel, myPath, true));
			} else {
				choicesDiv.appendChild(build(choice, myModel, myPath));				
			}
		}
		
		var div = document.createElement("div");
		div.className = "form-choice";
		div.appendChild(makeLabel("choice", myPath.join(".")));
		div.appendChild(choicesDiv);
		return div;
	}
	
	function buildGroup(spec, model, path) {
		var name = spec.name;
		var parts = spec.body;
		var myModel = model[name] || {};
		var myPath = path.concat([name]);
		
		var partsDiv = document.createElement("div");
		for (var i = 0; i < parts.length; i++) {
			partsDiv.appendChild(build(parts[i], myModel, myPath));
		}
		
		var div = document.createElement("div");
		div.className = "form-group";
		div.appendChild(makeLabel("group", myPath.join(".")));
		div.appendChild(partsDiv);
		return div;
	}
	
	function buildUnknown(spec, model, path) {
		error("Unknown element type:", spec.type, path);
		var div = document.createElement("div");
		div.appendChild(document.createTextNode("Unknown element type: " + spec.type + " (path = " + path.join(".") + ")"));
		return div;
	}
	
	var builders = {
		group: buildGroup,
		string: buildString,
		text: buildText,
		choice: buildChoice,
		checkbox: buildCheckbox,
		select: buildSelect,
		date: buildDate,
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
