var docgen = (function () {
  "use strict";

  function makeButton(id, label) {

    var appendButton = document.createElement("button");
    appendButton.id = id;
    appendButton.className = "btn";
    appendButton.innerHTML = label;
    return appendButton;
  }

  LUPAPISTE.DocModel = function (schema, model, removeCallback, docId, application) {

    // Magic key: if schema contains "_selected" radioGroup,
    // user can select only one of the schemas named in "_selected" group
    var SELECT_ONE_OF_GROUP_KEY = "_selected";

    var self = this;

    self.schema = schema;
    self.schemaName = schema.info.name;
    self.model = model;
    self.removeCallback = removeCallback;
    self.docId = docId;
    self.appId = application.id;
    self.application = application;
    self.eventData = { doc: docId, app: self.appId };

    self.sizeClasses = { "s": "form-input short", "m": "form-input medium" };

    // Context help
    self.findHelpElement = function (e) {
      var event = getEvent(e);
      var input$ = $(event.target);
      var help$ = input$.siblings('.form-help');
      if (!help$.length) {
        help$ = input$.parent().siblings('.form-help');
      }
      return help$;
    };

    self.showHelp = function (e) {
      self.findHelpElement(e).fadeIn("slow").css("display", "block");
    };
    self.hideHelp = function (e) {
      self.findHelpElement(e).fadeOut("slow").css("display", "none");
    };

    // ID utilities

    function pathStrToID(pathStr) {
      return self.docId + "-" + pathStr.replace(/\./g, "-");;
    }

    function pathStrToLabelID(pathStr) {
      return "label-" + pathStrToID(pathStr);
    }

    function pathStrToGroupID(pathStr) {
      return "group-" + pathStrToID(pathStr);
    }

    function locKeyFromPath(pathStr) {
      return (self.schemaName + "." + pathStr.replace(/\.+\d+\./g, ".")).replace(/\.+/g, ".");
    }

    function makeLabel(type, pathStr, groupLabel) {
      var label = document.createElement("label");
      var path = groupLabel ? pathStr + "._group_label" : pathStr;
      var locKey = locKeyFromPath(path);

      label.id = pathStrToLabelID(pathStr);
      label.htmlFor = pathStrToID(pathStr);
      label.className = "form-label form-label-" + type;
      label.innerHTML = loc(locKey);
      return label;
    }

    function makeInput(type, pathStr, value, extraClass, readonly) {
      var input = document.createElement("input");
      input.id = pathStrToID(pathStr);
      input.name = docId + "." + pathStr;

      try {
        input.type = type;
      } catch (e) {
        // IE does not support HTML5 input types such as email
        input.type = "text";
      }

      input.className = "form-input " + type + " " + (extraClass || "");

      if (readonly) {
        input.readOnly = true;
      } else {
        input.onchange = save;
      }


      if (type === "checkbox") {
        input.checked = value;
      } else {
        input.value = value || "";
      }
      return input;
    }

    function makeEntrySpan(subSchema, pathStr) {
      var help = null;
      var helpLocKey = locKeyFromPath(pathStr + ".help");
      var span = document.createElement("span");
      span.className = "form-entry";

      // Display text areas in a wide container
      if (subSchema.type === "text") {
        span.className = "form-entry form-full-width";
      }

      // Override style with layout option
      if (subSchema.layout) {
        span.className = "form-entry form-" + subSchema.layout;
      }

      // Add span for help text
      if (loc.hasTerm(helpLocKey)) {
        help = document.createElement("span");
        help.className = "form-help";
        help.innerHTML = loc(helpLocKey);
        span.appendChild(help);
      }

      return span;
    }

    // Form field builders

    function buildCheckbox(subSchema, model, path) {
      var myPath = path.join(".");
      var span = makeEntrySpan(subSchema, myPath);
      span.appendChild(makeInput("checkbox", myPath, getModelValue(model, subSchema.name), subSchema.readonly));
      span.appendChild(makeLabel("checkbox", myPath));
      return span;
    }

    function setMaxLen(input, subSchema) {
      var maxLen = subSchema["max-len"] || 255; // if you change the default, change in model.clj, too
      input.setAttribute("maxlength", maxLen);
    }

    function buildString(subSchema, model, path, partOfChoice) {
      var myPath = path.join(".");
      var span = makeEntrySpan(subSchema, myPath);
      var type = (subSchema.subtype === "email") ? "email" : "text";
      var sizeClass = self.sizeClasses[subSchema.size] || "";
      var input = makeInput(type, myPath, getModelValue(model, subSchema.name), sizeClass, subSchema.readonly);
      setMaxLen(input, subSchema);

      span.appendChild(makeLabel(partOfChoice ? "string-choice" : "string", myPath));

      if (subSchema.unit) {
        var inputAndUnit = document.createElement("span");
        var unit = document.createElement("span");

        inputAndUnit.className = "form-input-and-unit";
        inputAndUnit.appendChild(input);

        unit.className = "form-string-unit";
        unit.appendChild(document.createTextNode(loc("unit." + subSchema.unit)));

        input.onfocus = self.showHelp;
        input.onblur = self.hideHelp;

        inputAndUnit.appendChild(unit);
        span.appendChild(inputAndUnit);

      } else {
        input.onfocus = self.showHelp;
        input.onblur = self.hideHelp;
        span.appendChild(input);
      }

      return span;
    }

    function getModelValue(model, name) {
      return model[name] ? model[name].value : "";
    }

    function buildText(subSchema, model, path) {
      var myPath = path.join(".");
      var input = document.createElement("textarea");
      var span = makeEntrySpan(subSchema, myPath);

      input.id = pathStrToID(myPath);

      input.onfocus = self.showHelp;
      input.onblur = self.hideHelp;

      input.name = myPath;
      input.setAttribute("rows", subSchema.rows || "10");
      input.setAttribute("cols", subSchema.cols || "40");
      setMaxLen(input, subSchema);

      if (subSchema.readonly) {
        input.readOnly = true;
      } else {
        input.onchange = save;
      }

      input.className = "form-input textarea";
      input.value = getModelValue(model, subSchema.name);

      span.appendChild(makeLabel("text", myPath));
      span.appendChild(input);
      return span;
    }

    function buildDate(subSchema, model, path) {
      var lang = loc.getCurrentLanguage();
      var myPath = path.join(".");
      var value = getModelValue(model, subSchema.name);

      var span = makeEntrySpan(subSchema, myPath);

      // TODO: readonly support

      span.appendChild(makeLabel("date", myPath));

      // date
      $("<input>", {
        id: pathStrToID(myPath),
        name: docId + "." + path,
        type: "text",
        "class": "form-input text form-date",
        value: value,
        change: save
      }).datepicker($.datepicker.regional[lang]).appendTo(span);

      return span;
    }

    function buildSelect(subSchema, model, path) {
      var myPath = path.join(".");
      var select = document.createElement("select");
      var selectedOption = getModelValue(model, subSchema.name);
      var option = document.createElement("option");
      var span = makeEntrySpan(subSchema, myPath);

      select.onfocus = self.showHelp;
      select.onblur = self.hideHelp;

      select.name = myPath;
      select.className = "form-input combobox";

      select.id = pathStrToID(myPath);

      if (subSchema.readonly) {
        select.readOnly = true;
      } else {
        select.onchange = save;
      }

      option.value = "";
      option.appendChild(document.createTextNode(loc("selectone")));
      if (selectedOption === "") {
        option.selected = "selected";
      }
      select.appendChild(option);

      $.each(subSchema.body, function (i, o) {
        var name = o.name;
        var option = document.createElement("option");
        var locKey = self.schemaName + "." + myPath.replace(/\.\d+\./g, ".") + "." + name;

        option.value = name;
        option.appendChild(document.createTextNode(loc(locKey)));
        if (selectedOption === name) {
          option.selected = "selected";
        }
        select.appendChild(option);
      });

      span.appendChild(makeLabel("select", myPath, true));
      span.appendChild(select);
      return span;
    }

    function buildGroup(subSchema, model, path, partOfChoice) {
      var myPath = path.join(".");
      var name = subSchema.name;
      var myModel = model[name] || {};
      var partsDiv = document.createElement("div");
      var div = document.createElement("div");
      var clearDiv = document.createElement("div");

      appendElements(partsDiv, subSchema, myModel, path, save, partOfChoice);

      div.id = pathStrToGroupID(myPath);
      div.className = subSchema.layout === "vertical" ? "form-choice" : "form-group";
      clearDiv.className = "clear";
      div.appendChild(makeLabel("group", myPath, true));
      div.appendChild(partsDiv);
      div.appendChild(clearDiv);
      return div;
    }

    function buildRadioGroup(subSchema, model, path) {
      var myPath = path.join(".");
      var myModel;
      if (model[subSchema.name] && model[subSchema.name].value) {
        myModel = model[subSchema.name].value;
      } else {
        myModel = _.first(subSchema.body).name;
      }

      var partsDiv = document.createElement("div");
      var clearDiv = document.createElement("div");
      clearDiv.className = "clear";
      var span = makeEntrySpan(subSchema, myPath);

      partsDiv.id = pathStrToID(myPath);

      $.each(subSchema.body, function (i, o) {
        var pathForId = myPath + "." + o.name;
        var input = makeInput("radio", myPath, o.name, subSchema.readonly);
        input.id = pathStrToID(pathForId);
        input.checked = o.name === myModel;

        span.appendChild(input);
        span.appendChild(makeLabel("radio", pathForId));
      });

      partsDiv.appendChild(span);
      partsDiv.appendChild(clearDiv);
      return partsDiv;
    }

    function buildBuildingSelector(subSchema, model, path) {
      var myPath = path.join(".");
      var select = document.createElement("select");
      var selectedOption = getModelValue(model, subSchema.name);
      var option = document.createElement("option");
      var span = makeEntrySpan(subSchema, myPath);

      select.id = pathStrToID(myPath);

      //TODO: Tuki readonlylle
      select.name = myPath;
      select.className = "form-input combobox really-long";
      select.onchange = function (e) {
        var event = getEvent(e);
        var target = event.target;

        var buildingId = target.value;
        ajax
          .command("merge-details-from-krysp", { id: self.appId, documentId: docId, buildingId: buildingId })
          .success(function () {
            save(event);
            repository.load(self.appId);
          })
          .call();
        return false;
      };

      option.value = "";
      option.appendChild(document.createTextNode(loc("selectone")));
      if (selectedOption === "") {
        option.selected = "selected";
      }
      select.appendChild(option);

      ajax
        .command("get-building-info-from-legacy", { id: self.appId })
        .success(function (data) {
          $.each(data.data, function (i, building) {
            var name = building.buildingId;
            var usage = building.usage;
            var created = building.created;
            var option = document.createElement("option");
            option.value = name;
            option.appendChild(document.createTextNode(name + " (" + usage + ") - " + created));
            if (selectedOption === name) {
              option.selected = "selected";
            }
            select.appendChild(option);
          });
        })
        .error(function (error) {
          var text = error.text;
          var option = document.createElement("option");
          option.value = name;
          option.appendChild(document.createTextNode(loc("error." + text)));
          option.selected = "selected";
          select.appendChild(option);
          select.setAttribute("disabled", true);
        })
        .call();

      span.appendChild(makeLabel("select", "", "buildingSelector", true));
      span.appendChild(select);
      return span;
    }

    function buildPersonSelector(subSchema, model, path) {
      var myPath = path.join(".");
      var span = makeEntrySpan(subSchema, myPath);
      var myNs = path.slice(0, path.length - 1).join(".");
      var select = document.createElement("select");
      var selectedOption = getModelValue(model, subSchema.name);
      var option = document.createElement("option");
      select.id = pathStrToID(myPath);
      select.name = myPath;
      select.className = "form-input combobox long";
      select.onchange = function (e) {
        var event = getEvent(e);
        var target = event.target;
        var userId = target.value;
        ajax
          .command("set-user-to-document", { id: self.appId, documentId: docId, userId: userId, path: myNs })
          .success(function () {
            save(event, function () { repository.load(self.appId); });
          })
          .call();
        return false;
      };
      option.value = "";
      option.appendChild(document.createTextNode(loc("selectone")));
      if (selectedOption === "") {
        option.selected = "selected";
      }
      select.appendChild(option);

      _.each(self.application.auth, function (user) {
        // LUPA-89: don't print fully empty names
        if (user.firstName && user.lastName) {
          var option = document.createElement("option");
          var value = user.id;
          option.value = value;
          option.appendChild(document.createTextNode(user.firstName + " " + user.lastName));
          if (selectedOption === value) {
            option.selected = "selected";
          }
          select.appendChild(option);
        }
      });

      var label = document.createElement("label");
      var locKey = ('person-selector');
      label.className = "form-label form-label-select";
      label.innerHTML = loc(locKey);
      span.appendChild(label);
      span.appendChild(select);

      // new invite
      $("<button>", {
        "class": "icon-remove",
        "data-test-id": "application-invite-" + self.schemaName,
        text: loc("personSelector.invite"),
        click: function () {
          $("#invite-document-name").val(self.schemaName).change();
          $("#invite-document-path").val(myNs).change();
          $("#invite-document-id").val(self.docId).change();
          LUPAPISTE.ModalDialog.open("#dialog-valtuutus");
          return false;
        }
      }).appendTo(span);

      return span;
    }

    function buildUnknown(subSchema, model, path) {
      var div = document.createElement("div");

      error("Unknown element type:", subSchema.type, path);
      div.appendChild(document.createTextNode("Unknown element type: " + subSchema.type + " (path = " + path.join(".") + ")"));
      return div;
    }

    var builders = {
      group: buildGroup,
      string: buildString,
      text: buildText,
      checkbox: buildCheckbox,
      select: buildSelect,
      radioGroup: buildRadioGroup,
      date: buildDate,
      element: buildElement,
      buildingSelector: buildBuildingSelector,
      personSelector: buildPersonSelector,
      unknown: buildUnknown
    };

    function build(subSchema, model, path, partOfChoice) {
      var myName = subSchema.name;
      var myPath = path.concat([myName]);
      var builder = builders[subSchema.type] || buildUnknown;
      var repeatingId = myPath.join("-");

      function makeElem(myModel, id) {
        var elem = builder(subSchema, myModel, myPath.concat([id]), partOfChoice);
        elem.setAttribute("data-repeating-id", repeatingId);
        elem.setAttribute("data-repeating-id-" + repeatingId, id);
        if (subSchema.type === "group") {
          var clearDiv = document.createElement("div");
          clearDiv.className = "clear";
          elem.appendChild(clearDiv);
        }
        return elem;
      }

      if (subSchema.repeating) {
        var models = model[myName] || [{}];
        var elements = _.map(models, function (val, key) {
          var myModel = {};
          myModel[myName] = val;
          return makeElem(myModel, key);
        });

        var appendButton = makeButton(myPath.join("_") + "_append", loc(self.schemaName + "." + myPath.join(".") + "._append_label"));

        var appender = function () {
          var parent$ = $(this.parentNode);
          var count = parent$.children("*[data-repeating-id='" + repeatingId + "']").length;
          while (parent$.children("*[data-repeating-id-" + repeatingId + "='" + count + "']").length) {
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

      return builder(subSchema, model, myPath, partOfChoice);
    }

    function getSelectOneOfDefinition(schema) {
      var selectOneOfSchema = _.find(schema.body, function (subSchema) {
        return subSchema.name === SELECT_ONE_OF_GROUP_KEY && subSchema.type === "radioGroup";
      });

      if (selectOneOfSchema) {
        return _.map(selectOneOfSchema.body, function (subSchema) { return subSchema.name; }) || [];
      }

      return [];
    }

    function appendElements(body, schema, model, path, partOfChoice) {

      function toggleSelectedGroup(value) {
        $(body)
          .children("[data-select-one-of]")
          .hide()
          .filter("[data-select-one-of='" + value + "']")
          .show();
      }

      var selectOneOf = getSelectOneOfDefinition(schema);

      _.each(schema.body, function (subSchema) {
        var children = build(subSchema, model, path, save, partOfChoice);
        if (!_.isArray(children)) {
          children = [children];
        }
        _.each(children, function (elem) {
          if (_.indexOf(selectOneOf, subSchema.name) >= 0) {
            elem.setAttribute("data-select-one-of", subSchema.name);
            $(elem).hide();
          }

          body.appendChild(elem);
        });
      });

      if (selectOneOf.length) {
        // Show current selection or the first of the group
        var myModel = _.first(selectOneOf);
        if (model[SELECT_ONE_OF_GROUP_KEY]) {
          myModel = model[SELECT_ONE_OF_GROUP_KEY].value;
        }

        toggleSelectedGroup(myModel);

        var s = "[name$='." + SELECT_ONE_OF_GROUP_KEY + "']";
        $(body).find(s).change(function () {
          toggleSelectedGroup(this.value);
        });
      }

      return body;
    }

    function loaderImg() {
      var img = document.createElement("img");
      img.src = "/img/ajax-loader-12.gif";
      img.alt = "...";
      img.width = 12;
      img.height = 12;
      return img;
    }

    function saveForReal(path, value, callback) {
      var unPimpedPath = path.replace(new RegExp("^" + self.docId + "."), "");
      ajax
        .command("update-doc", { doc: self.docId, id: self.appId, updates: [[unPimpedPath, value]] })
        // Server returns empty array (all ok), or array containing an array with three
        // elements: [key status message]. Here we use just the status.
        .success(function (e) {
          var status = (e.results.length === 0) ? "ok" : e.results[0].result[0];
          callback(status,e.results);
        })
        .error(function (e) { error(e); callback("err"); })
        .fail(function (e) { error(e); callback("err"); })
        .call();
    }

    function showValidationResults(results) {
      $("#document-"+docId+" :input").removeClass("warning").removeClass("error");
      if(results && results.length > 0) {
        _.each(results,function(result) { $("#"+docId+"-"+result.path.join("-")).addClass("warning"); });
      }
    }

    function validate() {
      ajax
        .query("validate-doc", { id: self.appId, doc: self.docId})
        .success(function (e) { showValidationResults(e.results); })
        .call();
    }

    function save(e, callback) {
      var event = getEvent(e);
      var target = event.target;
      if (target.parentNode.indicator) {
        $(target.parentNode.indicator).fadeOut(200, function () { target.removeChild(indicator); });
      }
      var indicator = document.createElement("span");
      $(indicator).addClass("form-indicator");
      target.parentNode.appendChild(indicator);
      var path = target.name;
      var loader = loaderImg();
      var label = document.getElementById(pathStrToLabelID(path));
      var value = target.value;
      if (target.type === "checkbox") {
        value = target.checked;
      }
      if (label) {
        label.appendChild(loader);
      }

      function showIndicator(className, locKey) {
        $(indicator).addClass(className).text(loc(locKey));
        $(indicator).fadeIn(200);
        setTimeout(function () {
          $(indicator).removeClass(className);
          $(indicator).fadeOut(200, function () { target.parentNode.removeChild(indicator); });
        }, 2000);
      }

      saveForReal(path, value, function (status,results) {
        showValidationResults(results);
        if (label) {
          label.removeChild(loader);
        }
        if (status === "warn") {
          showIndicator("form-input-saved", "form.saved");
        } else if (status === "err") {
          showIndicator("form-input-err", "form.err");
        } else if (status === "ok") {
          showIndicator("form-input-saved", "form.saved");
        } else if (status !== "ok") {
          error("Unknown status:", status, "path:", path);
        }
        if (callback) { callback(); }
        // No return value or stoping the event propagation:
        // That would prevent moving to the next field with tab key in IE8.
      });
    }

    function removeThis() {
      this.parent().slideUp(function () { $(this).remove(); });
    }

    function removeDoc(e) {
      var n = $(e.target).parent();
      var op = self.schema.info.op;

      var documentName = loc(self.schemaName + "._group_label");
      if (op) {
        documentName = loc(op.name + "._group_label");
      }

      self.removeCallback(self.appId, self.docId, documentName, removeThis.bind(n));
      return false;
    }

    function buildElement() {
      var op = self.schema.info.op;

      var section = document.createElement("section");
      var icon = document.createElement("span");
      var title = document.createElement("h2");

      var sectionContainer = document.createElement("div");
      var elements = document.createElement("article");

      section.className = "accordion";
      icon.className = "icon toggle-icon drill-down-white";
      title.appendChild(icon);

      if (op) {
        title.appendChild(document.createTextNode(loc(op.name + "._group_label")));
      } else {
        title.appendChild(document.createTextNode(loc(self.schemaName + "._group_label")));
      }
      title.setAttribute("data-doc-id", self.docId);
      title.setAttribute("data-app-id", self.appId);
      title.onclick = accordion.click;
      if (self.schema.info.removable) {
        $(title)
          .append($("<span>")
            .addClass("icon remove inline-right")
            .attr("data-test-class", "delete-schemas." + self.schemaName)
            .click(removeDoc));
      }

      sectionContainer.className = "accordion_content expanded";
      sectionContainer.id = "document-"+docId;

      appendElements(elements, self.schema, self.model, []);

      sectionContainer.appendChild(elements);
      section.appendChild(title);
      section.appendChild(sectionContainer);

      return section;
    }

    self.element = buildElement();
    validate();
  };

  function displayDocuments(containerSelector, removeDocModel, application, documents) {

    function getDocumentOrder(doc) {
      var num = doc.schema.info.order || 7;
      return num * 10000000000 + doc.created / 1000;
    }

    var sortedDocs = _.sortBy(documents, getDocumentOrder);

    var docgenDiv = $(containerSelector).empty();
    _.each(sortedDocs, function (doc) {
      var schema = doc.schema;

      docgenDiv.append(new LUPAPISTE.DocModel(schema, doc.data, removeDocModel.init, doc.id, application).element);

      if (schema.info.repeating) {
        var btn = makeButton(schema.info.name + "_append_btn", loc(schema.info.name + "._append_label"));

        $(btn).click(function () {
          var self = this;
          ajax
            .command("create-doc", { schemaName: schema.info.name, id: application.id })
            .success(function (data) {
              var newDocId = data.doc;
              var newElem = new LUPAPISTE.DocModel(schema, {}, removeDocModel.init, newDocId, application).element;
              $(self).before(newElem);
            })
            .call();
        });
        docgenDiv.append(btn);
      }
    });
  }

  return {
    displayDocuments: displayDocuments
  };

})();
