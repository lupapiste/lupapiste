var docgen = (function () {

  function makeLabel(type, path, specId) {
    var label = document.createElement("label");
    label.id = path.replace(/\./g, "-");
    label.className = "form-label form-label-" + type;
    label.appendChild(document.createTextNode(loc(specId + "." + path)));
    return label;
  }

  function makeInput(type, path, value, save, extraClass) {
    var input = document.createElement("input");
    input.name = path;
    input.type = type;
    input.className = "form-input " + type + " " + (extraClass || "");
    input.onchange = save;
    if (type === "checkbox") {
      if (value) input.checked = "checked";
    } else {
      input.value = value || "";
    }
    return input;
  }

  function buildCheckbox(spec, model, path, save, specId) {
    var myPath = path.concat([spec.name]).join(".");
    var span = document.createElement("span");
    span.className = "form-entry";
    span.appendChild(makeInput("checkbox", myPath, model[spec.name], save));
    span.appendChild(makeLabel("checkbox", myPath, specId));
    return span;
  }

  function buildString(spec, model, path, save, specId, partOfChoice) {
    var myPath = path.concat([spec.name]).join(".");
    var div = document.createElement("span");
    var sizeClass = "";
    if (spec.size) {
      if (spec.size === "s") sizeClass = "form-input small";
      if (spec.size === "l") sizeClass = "form-string-large";
    }
    div.className = "form-entry";
    div.appendChild(makeLabel(partOfChoice ? "string-choice" : "string", myPath, specId));
    div.appendChild(makeInput("text", myPath, model[spec.name], save, sizeClass));
    if (spec.unit) {
      var unit = document.createElement("span");
      unit.className = "form-string-unit";
      unit.appendChild(document.createTextNode(loc("unit." + spec.unit)));
      div.appendChild(unit);
    }
    return div;
  }

  function buildText(spec, model, path, save, specId) {
    var myPath = path.concat([spec.name]).join(".");

    var input = document.createElement("textarea");
    input.name = myPath;
    input.setAttribute("rows", spec["rows"] || "10");
    input.setAttribute("cols", spec["cols"] || "40");
    input.className = "form-input textarea";
    input.onchange = save;
    input.value = model[spec.name] || "";

    var div = document.createElement("span");
    div.className = "form-entry";
    div.appendChild(makeLabel("text", myPath, specId));
    div.appendChild(input);
    return div;
  }

  function buildDate(spec, model, path, save, specId) {
    var myPath = path.concat([spec.name]).join(".");

    var input = document.createElement("input");
    input.setAttribute("type", "date");
    input.name = myPath;
    input.className = "form-input form-date";
    input.onchange = save;
    input.value = model[spec.name] || "";

    var div = document.createElement("span");
    div.className = "form-entry";
    div.appendChild(makeLabel("date", myPath, specId));
    div.appendChild(input);
    return div;
  }

  function buildSelect(spec, model, path, save, specId) {
    var myPath = path.concat([spec.name]).join(".");

    var select = document.createElement("select");
    select.name = myPath;
    select.className = "form-input combobox";
    select.onchange = save;

    var selectedOption = model[spec.name] || "";

    var option = document.createElement("option");
    option.value = "";
    option.appendChild(document.createTextNode(loc("selectone")));
    if (selectedOption === "") option.selected = "selected";
    select.appendChild(option);

    $.each(spec.body, function (i, o) {
      var name = o.name;
      var option = document.createElement("option");
      option.value = name;
      option.appendChild(document.createTextNode(loc(specId + "." + myPath + "." + name)));
      if (selectedOption === name) option.selected = "selected";
      select.appendChild(option);
    });

    var div = document.createElement("span");
    div.className = "form-entry";
    div.appendChild(makeLabel("select", myPath, specId));
    div.appendChild(select);
    return div;
  }

  function buildChoice(spec, model, path, save, specId) {
    var name = spec.name;
    var choices = spec.body;
    var myModel = model[name] || {};
    var myPath = path.concat([name]);

    var choicesDiv = document.createElement("div");
    $.each(choices, function (i, choice) {
      if (choice.type === "string") {
        choicesDiv.appendChild(buildString(choice, myModel, myPath, save, specId, true));
      } else {
        choicesDiv.appendChild(build(choice, myModel, myPath, save, specId));
      }
    })

    var div = document.createElement("div");
    div.className = "form-choice";
    div.appendChild(makeLabel("choice", myPath.join("."), specId));
    div.appendChild(choicesDiv);
    return div;
  }

  function buildGroup(spec, model, path, save, specId) {
    var name = spec.name;
    var parts = spec.body;
    var myModel = model[name] || {};
    var myPath = path.concat([name]);

    var partsDiv = document.createElement("div");
    for (var i = 0; i < parts.length; i++) {
      partsDiv.appendChild(build(parts[i], myModel, myPath, save, specId));
    }

    var div = document.createElement("div");
    div.className = "form-group";
    div.appendChild(makeLabel("group", myPath.join(".")));
    div.appendChild(partsDiv);
    return div;
  }

  function buildUnknown(spec, model, path, save, specId) {
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

  function build(spec, model, path, save, specId) {
    var builder = builders[spec.type] || buildUnknown;
    return builder(spec, model, path, save, specId);
  }

  function appendElements(body, specs, model, path, save, specId) {
    var l = specs.length;
    for (var i = 0; i < l; i++) {
      body.appendChild(build(specs[i], model, path, save, specId));
    }
    return body;
  }

  function loaderImg() {
    var img = document.createElement("img");
    img.src = "/img/ajax-loader-12.gif";
    return img;
  }

  function removeClass(target, classNames) {
    var names = target.className.split(/\s+/);
    _.each((typeof classNames === "string") ? [classNames] : classNames, function (className) { names = _.without(names, className); });
    target.className = names.join(" ");
  }

  function addClass(target, classNames) {
    var names = target.className.split(/\s+/);
    _.each((typeof classNames === "string") ? [classNames] : classNames, function (className) { names.push(className); });
    target.className = names.join(" ");
  }

  function makeSaverDelegate(save, eventData) {
    return function (e) {
      e.preventDefault();
      e.stopPropagation();

      var target = e.target;
      var path = target.name;
      var value = (target.type === "checkbox") ? target.checked : target.value;

      var loader = loaderImg();
      var label = document.getElementById(path.replace(/\./g, "-"));
      label.appendChild(loader);

      save(path, value, function (result) {
        label.removeChild(loader);
        removeClass(target, ["form-input-warn", "form-input-err"]);
        if (result === "ok") {
          // Nada.
        } else if (result === "warn") {
          addClass(target, "form-input-warn");
        } else if (result === "err") {
          addClass(target, "form-input-err");
        } else {
          error("Unknown result:", result, "path:", path);
        }
      }, eventData);

      return false;
    };
  }

  function buildElement(spec, model, save, specId) {
    var section = document.createElement("section");
    section.className = "accordion";

    var title = document.createElement("h2");
    title.appendChild(document.createTextNode(loc(specId)));
    title.onclick = accordion.toggle;

    var sectionContainer = document.createElement("div");
    sectionContainer.className = "application_section_content content_expanded";

    var elements = document.createElement("article");
    appendElements(elements, spec.body, model, [], save, specId);

    sectionContainer.appendChild(elements);
    section.appendChild(title);
    section.appendChild(sectionContainer);

    return section;
  }

  function DocModel(spec, model, callback, eventData) {
    var self = this;

    self.spec = spec;
    self.model = model;
    self.callback = callback;
    self.eventData = eventData;

    self.element = buildElement(self.spec, self.model, makeSaverDelegate(self.callback, self.eventData), self.spec.info.name);

    self.applyUpdates = function (updates) {
      // TODO: Implement me.
      $.each(updates, function (i, u) {
        debug("update", u[0], u[1]);
      });
    };
  }

  function makeDocModel(spec, model, callback, eventData) {
    return new DocModel(spec, model, callback, eventData || {});
  }

  return {
    build: makeDocModel
  };

})();
