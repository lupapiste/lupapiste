var docgen = (function() {
  "use strict";

  function makeButton(id, label) {

    var appendButton = document.createElement("button");
    appendButton.id = id;
    appendButton.className = "btn";
    appendButton.innerHTML = label;
    return appendButton;
  }

  LUPAPISTE.DocModel = function(schema, model, saveCallback, removeCallback, docId, appId) {

    // Magic key: if schema contains "_selected" radioGroup,
    // user can select only one of the schemas named in "_selected" group
    var SELECT_ONE_OF_GROUP_KEY = "_selected";

    var self = this;

    self.schema = schema;
    self.schemaName = schema.info.name;
    self.model = model;
    self.saveCallback = saveCallback;
    self.removeCallback = removeCallback;
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

    function makeLabel(type, pathStr, groupLabel) {
      var label = document.createElement("label");
      label.id = pathStrToLabelID(pathStr);
      label.htmlFor = pathStrToID(pathStr);
      label.className = "form-label form-label-" + type;

      var path = groupLabel ? pathStr + "._group_label" : pathStr;
      var locKey = (self.schemaName + "." + path.replace(/\.+\d+\./g, ".")).replace(/\.+/g, ".");
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

    function buildCheckbox(subSchema, model, path, save) {
      var myPath = path.join(".");
      var span = makeEntrySpan();
      span.appendChild(makeInput("checkbox", myPath, model[subSchema.name], save));
      span.appendChild(makeLabel("checkbox", myPath));
      return span;
    }

    function buildString(subSchema, model, path, save, partOfChoice) {
      var myPath = path.join(".");
      var span =  makeEntrySpan();
      var type = (subSchema.subtype === "email") ? "email" : "text";
      var sizeClass = self.sizeClasses[subSchema.size] || "";
      var input = makeInput(type, myPath, model[subSchema.name], save, sizeClass);

      span.appendChild(makeLabel(partOfChoice ? "string-choice" : "string", myPath));

      if (subSchema.unit) {
        var inputAndUnit = document.createElement("span");
        inputAndUnit.className = "form-input-and-unit";
        inputAndUnit.appendChild(input);

        var unit = document.createElement("span");
        unit.className = "form-string-unit";
        unit.appendChild(document.createTextNode(loc("unit." + subSchema.unit)));
        inputAndUnit.appendChild(unit);
        span.appendChild(inputAndUnit);
      } else {
        span.appendChild(input);
      }

      return span;
    }

    function buildText(subSchema, model, path, save) {
      var myPath = path.join(".");
      var input = document.createElement("textarea");
      var span = makeEntrySpan();

      input.name = myPath;
      input.setAttribute("rows", subSchema.rows || "10");
      input.setAttribute("cols", subSchema.cols || "40");
      input.className = "form-input textarea";
      input.onchange = save;
      input.value = model[subSchema.name] || "";

      span.appendChild(makeLabel("text", myPath));
      span.appendChild(input);
      return span;
    }

    function buildDate(subSchema, model, path, save) {
      var lang = loc.getCurrentLanguage();
      var myPath = path.join(".");
      var value = model[subSchema.name] || "";
      var span = makeEntrySpan();

      span.appendChild(makeLabel("date", myPath));

      // date
      $("<input>", {
        id:    pathStrToID(myPath),
        name:  docId + "." + path,
        type:  "text",
        "class": "form-input text form-date",
        value: value,
        change: save,
      }).datepicker($.datepicker.regional[lang]).appendTo(span);

      return span;
    }

    function buildSelect(subSchema, model, path, save) {
      var myPath = path.join(".");
      var select = document.createElement("select");
      var selectedOption = model[subSchema.name] || "";
      var option = document.createElement("option");
      var span = makeEntrySpan();

      select.name = myPath;
      select.className = "form-input combobox";
      select.onchange = save;

      option.value = "";
      option.appendChild(document.createTextNode(loc("selectone")));
      if (selectedOption === "") {
        option.selected = "selected";
      }
      select.appendChild(option);

      $.each(subSchema.body, function (i, o) {
        var name = o.name;
        var option = document.createElement("option");
        option.value = name;
        var locKey = self.schemaName + "." + myPath.replace(/\.\d+\./g, ".") + "." + name;
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

    function buildGroup(subSchema, model, path, save, partOfChoice) {
      var myPath = path.join(".");
      var name = subSchema.name;
      var myModel = model[name] || {};
      var partsDiv = document.createElement("div");
      var div = document.createElement("div");

      appendElements(partsDiv, subSchema, myModel, path, save, partOfChoice);

      div.id = pathStrToGroupID(myPath);
      div.className = subSchema.layout === "vertical" ? "form-choice" : "form-group";
      div.appendChild(makeLabel("group", myPath, true));
      div.appendChild(partsDiv);
      return div;
    }

    function buildRadioGroup(subSchema, model, path, save) {
      var myPath = path.join(".");
      var myModel = model[subSchema.name] || _.first(subSchema.body).name;
      var partsDiv = document.createElement("div");
      var span = makeEntrySpan();

      partsDiv.id = pathStrToID(myPath);

      $.each(subSchema.body, function (i, o) {
        var pathForId = myPath + "." + o.name;
        var input = makeInput("radio", myPath, o.name, save);
        input.id = pathStrToID(pathForId);
        input.checked = o.name === myModel;

        span.appendChild(input);
        span.appendChild(makeLabel("radio", pathForId));
      });

      partsDiv.appendChild(span);
      return partsDiv;
    }

    function buildBuildingSelector(subSchema, model, path, save) {
      var myPath = path.join(".");
      var select = document.createElement("select");
      var selectedOption = model[subSchema.name] || "";
      var option = document.createElement("option");
      var span = makeEntrySpan();

      select.name = myPath;
      select.className = "form-input combobox really-long";
      select.onchange = function(event) {
        var target = getEvent(event).target;
        var buildingId = target.value;
        ajax
          .command("merge-details-from-krysp", {id: appId, buildingId: buildingId})
          .success(function() {
            save(event);
            repository.load(appId);
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
        .command("get-building-info-from-legacy", {id: appId})
        .success(function(data) {
          $.each(data.data, function (i, building) {
            var name = building.buildingId;
            var usage = building.usage;
            var created = building.created;
            var option = document.createElement("option");
            option.value = name;
            option.appendChild(document.createTextNode(name+" ("+usage+") - "+created));
            if (selectedOption === name) {
              option.selected = "selected";
            }
            select.appendChild(option);
          });
        })
        .error(function(error) {
          var text = error.text;
          var option = document.createElement("option");
          option.value = name;
          option.appendChild(document.createTextNode(loc("error."+text)));
          option.selected = "selected";
          select.appendChild(option);
          select.setAttribute("disabled", true);
        })
        .call();

      span.appendChild(makeLabel("select", "", "buildingSelector", true));
      span.appendChild(select);
      return span;
    }

    function buildPersonSelector(subSchema, model, path, save) {
      var span = makeEntrySpan();
      var myPath = path.join(".");
      var myNs = path.slice(0,path.length-1).join(".");
      var select = document.createElement("select");
      var selectedOption = model[subSchema.name] || "";

      select.name = myPath;
      select.className = "form-input combobox long";
      select.onchange = function(event) {
        var target = getEvent(event).target;
        var userId = target.value;
        ajax
          .command("set-user-to-document", {id: appId, documentId: docId, userId: userId, path: myNs})
          .success(function() {
            save(event,function() { repository.load(appId); });
          })
          .call();
        return false;
      };
      var option = document.createElement("option");
      option.value = "";
      option.appendChild(document.createTextNode(loc("selectone")));
      if (selectedOption === "") {
        option.selected = "selected";
      }
      select.appendChild(option);

      ajax
        .command("get-users-in-application", {id: appId})
        .success(function(data) {
          $.each(data.users, function (i, user) {
            // LUPA-89: don't print fully empty names
            if(user.firstName && user.lastName) {
              var option = document.createElement("option");
              var value = user.id;
              option.value = value;
              option.appendChild(document.createTextNode(user.firstName+" "+user.lastName));
              if (selectedOption === value) {
                option.selected = "selected";
              }
              select.appendChild(option);
            }
          });
        })
        .call();

      span.appendChild(makeLabel("select", "", "personSelector", true));
      span.appendChild(select);

      // new invite
      $("<button>", {
        "class": "icon-remove",
        "data-test-id": "application-invite-"+self.schemaName,
        text: loc("personSelector.invite"),
        click: function() {
          $("#invite-document-name").val(self.schemaName).change();
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

    function build(subSchema, model, path, save, partOfChoice) {

      var myName = subSchema.name;
      var myPath = path.concat([myName]);
      var builder = builders[subSchema.type] || buildUnknown;
      var repeatingId = myPath.join("-");

      function makeElem(myModel, id) {
        var elem = builder(subSchema, myModel, myPath.concat([id]), save, partOfChoice);
        elem.setAttribute("data-repeating-id", repeatingId);
        elem.setAttribute("data-repeating-id-" + repeatingId, id);
        return elem;
      }

      if (subSchema.repeating) {
        var models = model[myName] || [{}];

        var elements = _.map(models, function(val, key) {
          var myModel = {};
          myModel[myName] = val;
          return makeElem(myModel, key);
        });

        var appendButton = makeButton(myPath.join("_") + "_append", loc(self.schemaName + "."+  myPath.join(".") + "._append_label"));

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

      return builder(subSchema, model, myPath, save, partOfChoice);
    }

    function getSelectOneOfDefinition(schema) {
      var selectOneOfSchema = _.find(schema.body, function(subSchema){
        return subSchema.name === SELECT_ONE_OF_GROUP_KEY && subSchema.type === "radioGroup";
      });

      if (selectOneOfSchema) {
        return _.map(selectOneOfSchema.body, function(subSchema) {return subSchema.name;}) || [];
      }

      return [];
    }

    function appendElements(body, schema, model, path, save, partOfChoice) {

      function toggleSelectedGroup(value) {
        $(body)
          .children("[data-select-one-of]")
          .hide()
          .filter("[data-select-one-of='" + value + "']")
          .show();
      }

      var selectOneOf = getSelectOneOfDefinition(schema);

      _.each(schema.body, function(subSchema) {
          var children = build(subSchema, model, path, save, partOfChoice);
          if (!_.isArray(children)) {
            children = [children];
          }
          _.each(children, function(elem) {
            if (_.indexOf(selectOneOf, subSchema.name) >= 0) {
              elem.setAttribute("data-select-one-of", subSchema.name);
              $(elem).hide();
            }

            body.appendChild(elem);
          });
      });

      if (selectOneOf.length) {
        // Show current selection or the first of the group
        var myModel = model[SELECT_ONE_OF_GROUP_KEY] || _.first(selectOneOf);
        toggleSelectedGroup(myModel);

        var s = "[name$='." + SELECT_ONE_OF_GROUP_KEY + "']";
        $(body).find(s).change(function() {
          toggleSelectedGroup(this.value);
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
      return function (event, callback) {
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
          if (status === "warn") {
            $(target).addClass("form-input-warn");
          } else if (status === "err") {
            $(target).addClass("form-input-err");
          } else if (status !== "ok") {
            error("Unknown status:", status, "path:", path);
          }
          if(callback) { callback(); }
        }, eventData);
        // No return value or stoping the event propagation:
        // That would prevent moving to the next field with tab key in IE8.
      };
    }

    function removeThis() {
      this.parent().slideUp(function() { $(this).remove(); });
    }

    function removeDoc(e) {
      var n = $(e.target).parent();
      var op = self.schema.info.op;

      var documentName = loc(self.schemaName + "._group_label");
      if (op) {
        documentName = loc(op + "._group_label");
      }

      self.removeCallback(self.appId, self.docId, documentName, removeThis.bind(n));
      return false;
    }

    function buildElement() {
      var op = self.schema.info.op;
      var save = makeSaverDelegate(self.saveCallback, self.eventData);

      var section = document.createElement("section");
      var icon = document.createElement("span");
      var title = document.createElement("h2");

      var sectionContainer = document.createElement("div");
      var elements = document.createElement("article");

      section.className = "accordion";
      icon.className = "font-icon icon-expanded";
      title.appendChild(icon);

      if (op) {
        title.appendChild(document.createTextNode(loc(op + "._group_label")));
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
            .click(removeDoc));
      }

      sectionContainer.className = "accordion_content expanded";

      appendElements(elements, self.schema, self.model, [], save);

      sectionContainer.appendChild(elements);
      section.appendChild(title);
      section.appendChild(sectionContainer);

      return section;
    }

    self.element = buildElement();
  };

  var save = function(path, value, callback, data) {
    ajax
      .command("update-doc", {doc: data.doc, id: data.app, updates: [[path, value]]})
      // Server returns empty array (all ok), or array containing an array with three
      // elements: [key status message]. Here we use just the status.
      .success(function(e) {
        var status = (e.results.length === 0) ? "ok" : e.results[0][1];
        callback(status);
      })
      .error(function(e) { error(e); callback("err"); })
      .fail(function(e) { error(e); callback("err"); })
      .call();
  };

  function getDocumentOrder(doc) {
    var num = doc.schema.info.order || 7;
    return num * 10000000000 + doc.created/1000;
  }

  function displayDocuments(containerSelector, removeDocModel, applicationId, documents) {

    var sortedDocs = _.sortBy(documents, getDocumentOrder);

    var docgenDiv = $(containerSelector).empty();
    _.each(sortedDocs, function(doc) {
      var schema = doc.schema;

      docgenDiv.append(new LUPAPISTE.DocModel(schema, doc.body, save, removeDocModel.init, doc.id, applicationId).element);

      if (schema.info.repeating) {
        var btn = makeButton(schema.info.name + "_append_btn", loc(schema.info.name + "._append_label"));

        $(btn).click(function() {
          var self = this;
          ajax
            .command("create-doc", {schema: schema.info.name, id: applicationId})
            .success(function(data) {
              var newDocId = data.doc;
              var newElem = new LUPAPISTE.DocModel(schema, {}, save, removeDocModel.init, newDocId, applicationId).element;
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
