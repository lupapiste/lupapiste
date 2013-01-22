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

  // Magic key: if schema contains "_selected" radioGroup,
  // user can select only one of the schemas named in "_selected" group
  var SELECT_ONE_OF_GROUP_KEY = "_selected";

  var self = this;

  self.spec = spec;
  self.model = model;
  self.callback = callback;
  self.docId = docId;
  self.appId = appId;
  self.eventData = {doc: docId, app: appId};

  self.sizeClasses = {"s" : "form-input short", "m" : "form-input medium"};

  // ID utilities

  function pathStrToID(pathStr) {
      return self.docId + pathStr.replace(/\./g, "-");
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
    input.name = docId + "." + path;

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

  function makeEntrySpan() {
    var span = document.createElement("span");
    span.className = "form-entry";
    return span;
  }

  // Form field builders

  function buildCheckbox(spec, model, path, save, specId) {
    var myPath = path.join(".");
    var span = makeEntrySpan();
    span.appendChild(makeInput("checkbox", myPath, model[spec.name], save));
    span.appendChild(makeLabel("checkbox", myPath, specId));
    return span;
  }

  function buildString(spec, model, path, save, specId, partOfChoice) {
    var myPath = path.join(".");
    var span =  makeEntrySpan();
    span.appendChild(makeLabel(partOfChoice ? "string-choice" : "string", myPath, specId));



    var type = (spec.subtype === "email") ? "email" : "text";
    var sizeClass = self.sizeClasses[spec.size] || "";
    var input = makeInput(type, myPath, model[spec.name], save, sizeClass)

    if (spec.unit) {
      var inputAndUnit = document.createElement("span");
      inputAndUnit.className = "form-input-and-unit";
      inputAndUnit.appendChild(input);

      var unit = document.createElement("span");
      unit.className = "form-string-unit";
      unit.appendChild(document.createTextNode(loc("unit." + spec.unit)));
      inputAndUnit.appendChild(unit);
      span.appendChild(inputAndUnit);
    } else {
      span.appendChild(input);
    }

    return span;
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

    var span = makeEntrySpan();
    span.appendChild(makeLabel("text", myPath, specId));
    span.appendChild(input);
    return span;
  }

  function buildDate(spec, model, path, save, specId) {
    var myPath = path.join(".");
    var value = model[spec.name] || "";
    var input = makeInput("date", myPath, value, save, "form-date");

    var span = makeEntrySpan();
    span.appendChild(makeLabel("date", myPath, specId));
    span.appendChild(input);
    return span;
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
      var locKey = specId + "." + myPath.replace(/\.\d+\./g, ".") + "." + name;
      option.appendChild(document.createTextNode(loc(locKey)));
      if (selectedOption === name) {
        option.selected = "selected";
      }
      select.appendChild(option);
    });

    var span = makeEntrySpan();
    span.appendChild(makeLabel("select", myPath, specId, true));
    span.appendChild(select);
    return span;
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
    var myPath = path.join(".");
    var name = spec.name;
    var myModel = model[name] || {};

    var partsDiv = document.createElement("div");
    appendElements(partsDiv, spec, myModel, path, save, specId);

    var div = document.createElement("div");
    div.id = pathStrToGroupID(myPath);
    div.className = "form-group";
    div.appendChild(makeLabel("group", myPath, specId, true));
    div.appendChild(partsDiv);
    return div;
  }

  function buildRadioGroup(spec, model, path, save, specId) {
    var myPath = path.join(".");
    var myModel = model[spec.name] || _.first(spec.body).name;

    var partsDiv = document.createElement("div");
    partsDiv.id = pathStrToID(myPath);

    var span = makeEntrySpan();

    $.each(spec.body, function (i, o) {
      var pathForId = myPath + "." + o.name;
      var input = makeInput("radio", myPath, o.name, save);
      input.id = pathStrToID(pathForId);
      input.checked = o.name === myModel;

      span.appendChild(input);
      span.appendChild(makeLabel("radio", pathForId, specId));
    });

    partsDiv.appendChild(span);
    return partsDiv;
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
    radioGroup: buildRadioGroup,
    date: buildDate,
    element: buildElement,
    unknown: buildUnknown
  };

  function build(spec, model, path, save, specId, partOfChoice) {

    var myName = spec.name;
    var myPath = path.concat([myName]);
    var builder = builders[spec.type] || buildUnknown;
    var repeatingId = myPath.join("-");

    function makeElem(myModel, id) {
      var elem = builder(spec, myModel, myPath.concat([id]), save, specId, partOfChoice);
      elem.setAttribute("data-repeating-id", repeatingId);
      elem.setAttribute("data-repeating-id-" + repeatingId, id);
      return elem;
    }
    
    if (spec.repeating) {
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

  function getSelectOneOfDefinition(schema) {
    var selectOneOfSchema = _.find(schema.body, function(spec){
      return spec.name === SELECT_ONE_OF_GROUP_KEY && spec.type === "radioGroup";
    });

    if (selectOneOfSchema) {
      return _.map(selectOneOfSchema.body, function(spec) {return spec.name;}) || [];
    }

    return [];
  }

  function appendElements(body, schema, model, path, save, specId, partOfChoice) {
    var selectOneOf = getSelectOneOfDefinition(schema);

    _.each(schema.body, function(spec) {
        var children = build(spec, model, path, save, specId, partOfChoice);
        if (!_.isArray(children)) {
          children = [children];
        }
        _.each(children, function(elem) {
          if (_.indexOf(selectOneOf, spec.name) >= 0) {
            elem.setAttribute("data-select-one-of", spec.name);
          }
          // Hide all but the first of the selections
          if (_.indexOf(selectOneOf, spec.name) > 0) {
            $(elem).hide();
          }
          body.appendChild(elem)
        });
    });

    if (selectOneOf.length) {
      var s = "[name$='." + SELECT_ONE_OF_GROUP_KEY + "']";
      $(body).find(s).change(function() {
        $(body).children("[data-select-one-of]").hide();
        $(body).children("[data-select-one-of='" + this.value + "']").show();
      });
    }

    return body;
  }

  function loaderImg() {
    var img = document.createElement("img");
    img.src = "/img/ajax-loader-12.gif";
    return img;
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
        $(target).removeClass("form-input-warn").removeClass("form-input-err");
        if (status === "ok") {
          // Nada.
        } else if (status === "warn") {
          $(target).addClass("form-input-warn");
        } else if (status === "err") {
          $(target).addClass("form-input-err");
        } else {
          error("Unknown result:", result, "path:", path);
        }
      }, eventData);
      // No return value or stoping the event propagation:
      // That would prevent moving to the next field with tab key in IE8.
    };
  }

  function removeDoc(e) {
    var n = $(e.target).parent();
    var docId = n.attr("data-doc-id");
    var appId = n.attr("data-app-id");
    ajax
      .command("remove-doc", {id: appId, docId: docId})
      .success(function() { n.parent().remove(); })
      .call();
    return false;
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
    title.setAttribute("data-doc-id", self.docId);
    title.setAttribute("data-app-id", self.appId);
    title.onclick = accordion.toggle;
    if (self.spec.info.removable) {
      $(title)
        .append($("<button>")
          .addClass("remove")
          .html("[remove]")
          .click(removeDoc));
    }

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
