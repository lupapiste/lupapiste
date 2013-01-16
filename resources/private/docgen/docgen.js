if (typeof LUPAPISTE === "undefined") {
  var LUPAPISTE = {};
}

LUPAPISTE.DOMUtils = {
  makeButton: function (id, label) {
    var appendButton = document.createElement("button");
    appendButton.id = id;
    appendButton.className = "btn";
    appendButton.innerHTML = label;
    return appendButton;
  }
};

LUPAPISTE.DocModel = function(spec, model, callback, docId, appId) {
  "use strict";

  var self = this;

  self.spec = spec;
  self.model = model;
  self.callback = callback;
  self.docId = docId;
  self.appId = appId;
  self.eventData = {doc: docId, app: appId};

  // ID utilities

  function pathStrToID(pathStr) {
      return docId + pathStr.replace(/\./g, "-");
  }

  function pathStrToLabelID(pathStr) {
    return "label-" + pathStrToID(pathStr);
  }

  function pathStrToGroupID(pathStr) {
    return "group-" + pathStrToID(pathStr);
  }

  function makeLabel(type, pathStr, specId, groupLabel) {
    var label = document.createElement("label");
    label.id = pathStrToLabelID(pathStr);
    label.htmlFor = pathStrToID(pathStr);
    label.className = "form-label form-label-" + type;

    var path = groupLabel ? pathStr + "._group_label" : pathStr;
    var locKey = specId + "." + path.replace(/\.\d+\./g, ".");
    label.innerHTML = loc(locKey);
    return label;
  }

  function makeInput(type, path, value, save, extraClass) {
    var input = document.createElement("input");
    input.id = pathStrToID(path);
    input.name = path;

    try {
      input.type = type;
    } catch (e) {
      // IE does not support HTML5 input types such as email
      input.type = "text";
    }

    input.className = "form-input " + type + " " + (extraClass || "");
    input.onchange = save;
    if (type === "checkbox") {
      input.checked = value;
    } else {
      input.value = value || "";
    }
    return input;
  }

  // Form field builders

  function buildCheckbox(spec, model, path, save, specId) {
    var myPath = path.join(".");
    var span = document.createElement("span");
    span.className = "form-entry";
    span.appendChild(makeInput("checkbox", myPath, model[spec.name], save));
    span.appendChild(makeLabel("checkbox", myPath, specId));
    return span;
  }

  function buildString(spec, model, path, save, specId, partOfChoice) {
    var myPath = path.join(".");
    var div = document.createElement("span");
    var sizeClass = "";
    if (spec.size) {
      if (spec.size === "s") sizeClass = "form-input short";
      if (spec.size === "m") sizeClass = "form-input medium";
    }
    div.className = "form-entry";
    div.appendChild(makeLabel(partOfChoice ? "string-choice" : "string", myPath, specId));
    var type = (spec.subtype === "email") ? "email" : "text";
    div.appendChild(makeInput(type, myPath, model[spec.name], save, sizeClass));
    if (spec.unit) {
      var unit = document.createElement("span");
      unit.className = "form-string-unit";
      unit.appendChild(document.createTextNode(loc("unit." + spec.unit)));
      div.appendChild(unit);
    }
    return div;
  }

  function buildText(spec, model, path, save, specId) {
    var myPath = path.join(".");

    var input = document.createElement("textarea");
    input.name = myPath;
    input.setAttribute("rows", spec.rows || "10");
    input.setAttribute("cols", spec.cols || "40");
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
    var myPath = path.join(".");

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
    var myPath = path.join(".");

    var select = document.createElement("select");
    select.name = myPath;
    select.className = "form-input combobox";
    select.onchange = save;

    var selectedOption = model[spec.name] || "";

    var option = document.createElement("option");
    option.value = "";
    option.appendChild(document.createTextNode(loc("selectone")));
      if (selectedOption === "") {
        option.selected = "selected";
      }
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
      div.appendChild(makeLabel("select", myPath, specId, true));
    div.appendChild(select);
    return div;
  }

  function buildChoice(spec, model, path, save, specId) {
    var name = spec.name;
    var myModel = model[name] || {};

    var choicesDiv = document.createElement("div");
      appendElements(choicesDiv, spec, myModel, path, save, specId, true);

    var div = document.createElement("div");
    div.className = "form-choice";
      div.appendChild(makeLabel("choice", path.join("."), specId, true));
    div.appendChild(choicesDiv);
    return div;
  }

  function buildGroup(spec, model, path, save, specId) {
    var name = spec.name;
    var myModel = model[name] || {};

    var partsDiv = document.createElement("div");
      appendElements(partsDiv, spec, myModel, path, save, specId);

      var pathStr = path.join(".");
    var div = document.createElement("div");
      div.id = pathStrToGroupID(pathStr);
    div.className = "form-group";
      div.appendChild(makeLabel("group", pathStr, specId, true));
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
    element: buildElement,
    unknown: buildUnknown
  };

  function build(spec, model, path, save, specId, partOfChoice) {

    var myName = spec.name;
    var myPath = path.concat([myName]);
    var builder = builders[spec.type] || buildUnknown;

    function makeElem(myModel, id) {
      var elem = builder(spec, myModel, myPath.concat([id]), save, specId, partOfChoice);
      elem.setAttribute("data-repeating-id", repeatingId);
      elem.setAttribute("data-repeating-id-" + repeatingId, id);
      return elem;
    }

    if (spec.repeating) {
      var repeatingId = myPath.join("-")
      var models = model[myName] || [{}];

      var elements = _.map(models, function(val, key) {
        var myModel = {};
        myModel[myName] = val;
        return makeElem(myModel, key);
      });

        var appendButton = LUPAPISTE.DOMUtils.makeButton(myPath.join("_") + "_append", loc(specId + "."+  myPath.join(".") + "._append_label"));

      var appender = function() {
        var parent$ = $(this.parentNode);
        var count = parent$.children("*[data-repeating-id='" + repeatingId + "']").length;
        while (parent$.children("*[data-repeating-id-" + repeatingId + "='"+ count + "']").length) {
          count++;
        }
        var myModel = {};
        myModel[myName] = {};
          $(this).before(makeElem(myModel, count));
      };

      $(appendButton).click(appender);
      elements.push(appendButton);

      return elements;
    }

    return builder(spec, model, myPath, save, specId, partOfChoice);
  }

  function appendElements(body, schema, model, path, save, specId, partOfChoice) {

    var selectOneOf = [];
    if (schema.info && schema.info.selectOneOf) {
      var selectOneOf = schema.info.selectOneOf;
    }
///////////////////////////////////////////////////////////////////////////////////////////
    _.each(selectOneOf, function(s, i) {
      var myPath = path.concat(["_selected"]).join(".");
      var id = pathStrToID(myPath + "." + s);

      var span = document.createElement("span");
      span.className = "form-entry";


      var input = makeInput("radio", myPath, s, save);
        input.id = id;
        input.checked = i === 0;

        $(input).change(function() {
          var v = this.value;
console.log(v);
          var parent$ = $(this.parentNode.parentNode);
          parent$.children("[data-select-one-of]").hide();
        parent$.children("[data-select-one-of='" + v + "']").show();
      });

      span.appendChild(input);
      span.appendChild(makeLabel("checkbox", myPath + "." + s, specId));
      body.appendChild(span);
    });

    _.each(schema.body, function(spec) {
    var children = build(spec, model, path, save, specId, partOfChoice);
    if (!_.isArray(children)) {
      children = [children];
    }
      _.each(children, function(elem) {
        if (selectOneOf.length) {
          elem.setAttribute("data-select-one-of", spec.name);

          if (selectOneOf[0] !== spec.name) {
            $(elem).hide();
          }
        }
        body.appendChild(elem)
    });
      });
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
    return function (event) {
      var target = getEvent(event).target;
      var path = target.name;
        var value = target.value;
        if (target.type === "checkbox") {
          value = target.checked;
        }

      var loader = loaderImg();
      var label = document.getElementById(pathStrToLabelID(path));
        if (label) {
      label.appendChild(loader);
        }

      save(path, value, function (status) {
          if (label) {
        label.removeChild(loader);
          }
        removeClass(target, ["form-input-warn", "form-input-err"]);
        if (status === "ok") {
          // Nada.
        } else if (status === "warn") {
          addClass(target, "form-input-warn");
        } else if (status === "err") {
          addClass(target, "form-input-err");
        } else {
          error("Unknown result:", result, "path:", path);
        }
      }, eventData);
      // No return value or stoping the event propagation:
      // That would prevent moving to the next field with tab key in IE8.
    };
  }

  function buildElement() {
    var specId = self.spec.info.name;
    var save = makeSaverDelegate(self.callback, self.eventData);

    var section = document.createElement("section");
    section.className = "application_section";

    var icon = document.createElement("span");
    icon.className = "font-icon icon-expanded";
    var title = document.createElement("h2");
    title.className = "application_section_header";
    title.appendChild(icon);
    title.appendChild(document.createTextNode(loc(specId + "._group_label")));

    title.onclick = accordion.toggle;

    var sectionContainer = document.createElement("div");
    sectionContainer.className = "application_section_content content_expanded";

    var elements = document.createElement("article");
      appendElements(elements, self.spec, self.model, [], save, specId);

    sectionContainer.appendChild(elements);
    section.appendChild(title);
    section.appendChild(sectionContainer);

    return section;

  }

  self.element = buildElement();
}
