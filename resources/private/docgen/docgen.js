var docgen = (function () {
  "use strict";

  function makeButton(id, label) {
    var button = document.createElement("button");
    button.id = id;
    button.className = "btn";
    button.innerHTML = label;
    return button;
  }

  var DocModel = function (schema, model, meta, docId, application, authorizationModel, options) {

    // Magic key: if schema contains "_selected" radioGroup,
    // user can select only one of the schemas named in "_selected" group
    var SELECT_ONE_OF_GROUP_KEY = "_selected";

    var self = this;

    self.schema = schema;
    self.schemaName = schema.info.name;
    self.schemaI18name = schema.info.i18name;
    if (!self.schemaI18name) {
        self.schemaI18name = self.schemaName;
    }
    self.model = model;
    self.meta = meta;
    self.docId = docId;
    self.appId = application.id;
    self.application = application;
    self.authorizationModel = authorizationModel;
    self.eventData = { doc: docId, app: self.appId };
    self.propertyId = application.propertyId;

    self.getMeta = function (path, m) {
      var meta = m ? m : self.meta;
      if (!path || !path.length) {
        return meta;
      }
      if (meta) {
        var key = path[0];
        var val = meta[key];
        if (path.length === 1) {
          return val;
        }
        return self.getMeta(path.splice(1, path.length - 1), val);
      }
    };

    self.sizeClasses = { "s": "form-input short", "m": "form-input medium" };

    // Context help
    self.addFocus = function (e) {
      var event = getEvent(e);
      var input$ = $(event.target);
      input$.focus();
    };

    self.findHelpElement = function (e) {
      var event = getEvent(e);
      var input$ = $(event.target);
      var help$ = input$.siblings('.form-help');
      if (!help$.length) {
        help$ = input$.parent().siblings('.form-help');
      }
      if (!help$.length) {
        return false;
      }
      return help$;
    };
    self.findErrorElement = function (e) {
      var event = getEvent(e);
      var input$ = $(event.target);
      var error$ = input$.siblings('.errorPanel');
      if (!error$.length) {
        error$ = input$.parent().siblings('.errorPanel');
      }
      if (!error$.length) {
        return false;
      }
      return error$;
    };

    self.showHelp = function (e) {
      var element = self.findHelpElement(e);
      if (element) {
        element.fadeIn("slow").css("display", "block");
        var st = $(window).scrollTop(); // Scroll Top
        var y = element.offset().top;
        if ((y - 80) < (st)) {
          $("html, body").animate({ scrollTop: y - 80 + "px" });
        }
      }
      self.showError(e);
    };
    self.hideHelp = function (e) {
      var element = self.findHelpElement(e);
      if (element) {
        element.fadeOut("slow").css("display", "none");
      }
      self.hideError(e);
    };

    self.showError = function (e) {
      var element = self.findErrorElement(e);
      if (element.children().size()) {
        element.fadeIn("slow").css("display", "block");
      }
    };

    self.hideError = function (e) {
      var element = self.findErrorElement(e);
      if (element) {
        element.fadeOut("slow").css("display", "none");
      }
    };
    // ID utilities

    function pathStrToID(pathStr) {
      return self.docId + "-" + pathStr.replace(/\./g, "-");
    }

    function pathStrToLabelID(pathStr) {
      return "label-" + pathStrToID(pathStr);
    }

    function pathStrToGroupID(pathStr) {
      return "group-" + pathStrToID(pathStr);
    }

    function locKeyFromPath(pathStr) {
      return (self.schemaI18name + "." + pathStr.replace(/\.+\d+\./g, ".")).replace(/\.+/g, ".");
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
      input.setAttribute("data-docgen-path", pathStr);

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

      // durable field error panels
      var errorPanel = document.createElement("span");
      errorPanel.className = "errorPanel";
      errorPanel.id = pathStrToID(pathStr) + "-errorPanel";
      span.appendChild(errorPanel);

      // Add span for help text
      if (loc.hasTerm(helpLocKey)) {
        help = document.createElement("span");
        help.className = "form-help";
        help.innerHTML = loc(helpLocKey);
        span.appendChild(help);
      }

      return span;
    }

    self.makeApprovalButtons = function (path, model) {
      var btnContainer$ = $("<span>").addClass("form-buttons");
      var statusContainer$ = $("<span>");
      var approvalContainer$ = $("<span>").addClass("form-approval-status").append(statusContainer$).append(btnContainer$);
      var approveButton$ = null;
      var rejectButton$ = null;
      var cmdArgs = { id: self.appId, doc: self.docId, path: path.join(".") };

      if (_.isEmpty(model) || !features.enabled('docIndicators')) {
        return approvalContainer$[0];
      }

      function setStatus(approval) {
        if (approval) {
          var text = loc("document." + approval.value);
          if (approval.user && approval.timestamp) {
            text += " (" + approval.user.firstName + " " + approval.user.lastName;
            text += " " + moment(approval.timestamp).format("D.M.YYYY HH:mm") + ")";
          }
          statusContainer$.text(text);
          statusContainer$.addClass("approval-" + approval.value);
        }
      }

      function makeApprovalButton(verb, noun, cssClass) {
        var cmd = verb + "-doc";
        var title = loc("document." + verb);
        return $(makeButton(self.docId + "_" + verb, title))
        .addClass(cssClass).addClass("btn-auto")
        .click(function () {
          ajax.command(cmd, cmdArgs)
          .success(function () {
            approveButton$.hide();
            rejectButton$.hide();
            setStatus({ value: noun });
          })
          .call();
        });
      }

      function modelModifiedSince(model, timestamp) {
        if (model) {
          if (!timestamp) {
            return true;
          }

          if (model.value) {
            // Leaf
            return model.modified && model.modified > timestamp;
          }
          return _.find(model, function (myModel) { return modelModifiedSince(myModel, timestamp); });
        }
        return false;
      }

      var meta = self.getMeta(path);
      var approval = meta ? meta._approved : null;

      if (self.authorizationModel.ok("approve-doc") &&
          self.authorizationModel.ok("reject-doc") &&
          (!approval || modelModifiedSince(model, approval.timestamp))) {
        approveButton$ = makeApprovalButton("approve", "approved", "btn-primary");
        btnContainer$.append(approveButton$);
        rejectButton$ = makeApprovalButton("reject", "rejected", "btn-secondary");
        btnContainer$.append(rejectButton$);
      } else {
        setStatus(approval);
      }
      return approvalContainer$[0];
    };

    // Form field builders

    function buildCheckbox(subSchema, model, path) {
      var myPath = path.join(".");
      var span = makeEntrySpan(subSchema, myPath);
      var input = makeInput("checkbox", myPath, getModelValue(model, subSchema.name), subSchema.readonly);
      var label = makeLabel("checkbox", myPath)
      input.onmouseover = self.showHelp;
      input.onmouseout = self.hideHelp;
      label.onmouseover = self.showHelp;
      label.onmouseout = self.hideHelp;
      span.appendChild(input);
      span.appendChild(label);
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

      if (subSchema.subtype === "maaraala-tunnus" ) {
          var kiitunAndInput = document.createElement("span");
          var kiintun = document.createElement("span");

          kiitunAndInput.className = "kiintun-and-maaraalatunnus";

          kiintun.className = "form-maaraala";
          kiintun.appendChild(document.createTextNode(util.prop.toHumanFormat(self.propertyId) + "-M"));

          input.onfocus = self.showHelp;
          input.onblur = self.hideHelp;
          input.onmouseover = self.showHelp;
          input.onmouseout = self.hideHelp;

          kiitunAndInput.appendChild(kiintun);
          kiitunAndInput.appendChild(input);
          span.appendChild(kiitunAndInput);

      }
      else if (subSchema.unit) {
        var inputAndUnit = document.createElement("span");
        var unit = document.createElement("span");

        inputAndUnit.className = "form-input-and-unit";
        inputAndUnit.appendChild(input);

        unit.className = "form-string-unit";
        unit.appendChild(document.createTextNode(loc("unit." + subSchema.unit)));

        input.onfocus = self.showHelp;
        input.onblur = self.hideHelp;
        input.onmouseover = self.showHelp;
        input.onmouseout = self.hideHelp;

        inputAndUnit.appendChild(unit);
        span.appendChild(inputAndUnit);

      } else {
        input.onfocus = self.showHelp;
        input.onblur = self.hideHelp;
        input.onmouseover = self.showHelp;
        input.onmouseout = self.hideHelp;
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
      input.onmouseover = self.showHelp;
      input.onmouseout = self.hideHelp;

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

      span.appendChild(makeLabel("date", myPath));

      // date
      var input = $("<input>", {
        id: pathStrToID(myPath),
        name: docId + "." + myPath,
        type: "text",
        "class": "form-input text form-date",
        value: value
      });

      if (subSchema.readonly) {
        input.attr("readonly", true);
      } else {
        input.datepicker($.datepicker.regional[lang]).change(save);
      }
      input.appendTo(span);

      return span;
    }

    function buildSelect(subSchema, model, path) {
      var myPath = path.join(".");
      var select = document.createElement("select");
      var selectedOption = getModelValue(model, subSchema.name);
      var span = makeEntrySpan(subSchema, myPath);

      select.onfocus = self.showHelp;
      select.onblur = self.hideHelp;
      select.onmouseover = self.showHelp;
      select.onmouseout = self.hideHelp;
      select.setAttribute("data-docgen-path", myPath);

      select.name = myPath;
      select.className = "form-input combobox";

      select.id = pathStrToID(myPath);

      if (subSchema.readonly) {
        select.readOnly = true;
      } else {
        select.onchange = save;
      }

      var otherKey = subSchema["other-key"];
      if (otherKey) {
        var pathToOther = path.slice(0, -1);
        pathToOther.push(otherKey);
        select.setAttribute("data-select-other-id", pathStrToID(pathToOther.join(".")));
      }

      var option = document.createElement("option");
      option.value = "";
      option.appendChild(document.createTextNode(loc("selectone")));
      if (selectedOption === "") option.selected = "selected";
      select.appendChild(option);

      $.each(subSchema.body, function (i, o) {
        var name = o.name;
        var option = document.createElement("option");
        var locKey = self.schemaI18name + "." + myPath.replace(/\.\d+\./g, ".") + "." + name;

        option.value = name;
        option.appendChild(document.createTextNode(loc(locKey)));
        if (selectedOption === name) {
          option.selected = "selected";
        }
        select.appendChild(option);
      });

      if (otherKey) {
        option = document.createElement("option");
        option.value = "other";
        option.appendChild(document.createTextNode(loc("select-other")));
        if (selectedOption === "other") option.selected = "selected";
        select.appendChild(option);
      }

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
      var label = makeLabel("group", myPath, true);

      appendElements(partsDiv, subSchema, myModel, path, save, partOfChoice);

      div.id = pathStrToGroupID(myPath);
      div.className = subSchema.layout === "vertical" ? "form-choice" : "form-group";

      div.appendChild(label);

      if (subSchema.approvable) {
        label.appendChild(self.makeApprovalButtons(path, myModel));
      }

      div.appendChild(partsDiv);
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

      var span = makeEntrySpan(subSchema, myPath);
      span.className = span.className + " radioGroup";
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
      return partsDiv;
    }

    function buildBuildingSelector(subSchema, model, path) {
      var myPath = path.join(".");
      var select = document.createElement("select");
      var selectedOption = getModelValue(model, subSchema.name);
      var option = document.createElement("option");
      var span = makeEntrySpan(subSchema, myPath);

      select.id = pathStrToID(myPath);

      select.name = myPath;
      select.className = "form-input combobox long";

      if (subSchema.readonly) {
        select.readOnly = true;
      } else {
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
      }

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
      span.className = span.className + " personSelector";
      var myNs = path.slice(0, path.length - 1).join(".");
      var select = document.createElement("select");
      var selectedOption = getModelValue(model, subSchema.name);
      var option = document.createElement("option");
      select.id = pathStrToID(myPath);
      select.name = myPath;
      select.className = "form-input combobox";
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

    function removeData(id, doc, path) {
      ajax
        .command("remove-document-data", { doc: doc, id: id, path: path })
        .success(function (e) {
          repository.load(id);
        })
        .call();
    }

    function build(subSchema, model, path, partOfChoice) {
      var myName = subSchema.name;
      var myPath = path.concat([myName]);
      var builder = builders[subSchema.type] || buildUnknown;
      var repeatingId = myPath.join("-");

      function makeElem(myModel, id) {
        var elem = builder(subSchema, myModel, myPath.concat([id]), partOfChoice);
        elem.setAttribute("data-repeating-id", repeatingId);
        elem.setAttribute("data-repeating-id-" + repeatingId, id);

        if (subSchema.repeating && !isDisabled(options) && features.enabled('removeRepeating') && authorizationModel.ok('remove-document-data')) {
          var removeButton = document.createElement("span");
          removeButton.className = "icon remove-grey inline-right";
          removeButton.setAttribute("data-test-class", "delete-schemas." + subSchema.name);
          removeButton.onclick = function () {
            LUPAPISTE.ModalDialog.showDynamicYesNo(loc("document.delete.header"), loc("document.delete.message"),
                { title: loc("yes"), fn: function () { removeData(self.appId, self.docId, myPath.concat([id])); } },
                { title: loc("no") });
          };
          elem.insertBefore(removeButton, elem.childNodes[0]);
        }

        if (subSchema.type === "group") {
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

        var appendButton = makeButton(myPath.join("_") + "_append", loc(self.schemaI18name + "." + myPath.join(".") + "._append_label"));

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
          callback(status, e.results);
        })
        .error(function (e) { error(e); callback("err"); })
        .fail(function (e) { error(e); callback("err"); })
        .call();
    }

    function showValidationResults(results) {
      // remove warning and error highlights
      $("#document-" + self.docId).find("*").removeClass("warn").removeClass("error").removeClass("tip");
      // clear validation errors
      $("#document-" + self.docId + " .errorPanel").html("").fadeOut();
      // apply new errors & highlights
      if (results && results.length > 0) {
        _.each(results, function (r) {
          var level = r.result[0],
              code = r.result[1],
              pathStr = r.path.join("-");

          if (level !== "tip") {
            var errorPanel$ = $("#" + self.docId + "-" + pathStr + "-errorPanel");
            errorPanel$.append("<span class='" + level + "'>" + loc("error." + code) + "</span>");
          }

          $("#" + pathStrToLabelID(pathStr)).addClass(level);
          $("#" + pathStrToID(pathStr)).addClass(level);
        });
      }
    }

    function validate() {
      if (!options || options.validate) {
        ajax
          .query("validate-doc", { id: self.appId, doc: self.docId })
          .success(function (e) { showValidationResults(e.results); })
          .call();
      }
    }

    function disableBasedOnOptions() {
      if (!self.authorizationModel.ok("update-doc") || options && options.disabled) {
        $(self.element).find('input, textarea').attr("readonly", true).unbind("focus");
        $(self.element).find('select, input[type=checkbox], input[type=radio]').attr("disabled", true);
        $(self.element).find('button').hide();
      }
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
        }, 4000);
      }

      saveForReal(path, value, function (status, results) {
        showValidationResults(results);
        if (label) {
          label.removeChild(loader);
        }
        if (status === "warn" || status === "tip") {
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

    function removeDoc(e) {
      var n$ = $(e.target).parent();
      while (!n$.is("section")) {
        n$ = n$.parent();
      }
      var op = self.schema.info.op;

      var documentName = loc(self.schemaI18name + "._group_label");
      if (op) {
        documentName = loc(op.name + "._group_label");
      }

      function onRemovalConfirmed() {
        ajax.command("remove-doc", { id: self.appId, docId: self.docId })
          .success(function () {
            n$.slideUp(function () { n$.remove(); });
            // This causes full re-rendering, all accordions change state etc. Figure a better way to update UI.
            // Just the "operations" list should be changed.
            repository.load(self.appId);
          })
          .call();
        return false;
      }

      var message = "<div>" + loc("removeDoc.message1") + " <strong>" + documentName + ".</strong></div><div>" + loc("removeDoc.message2") + "</div>";
      LUPAPISTE.ModalDialog.showDynamicYesNo(loc("removeDoc.sure"), message,
          { title: loc("removeDoc.ok"), fn: onRemovalConfirmed }, { title: loc("removeDoc.cancel") }, { html: true });

      return false;
    }

    function buildElement() {
      var op = self.schema.info.op;

      var section = document.createElement("section");
      var icon = document.createElement("span");
      var title = document.createElement("h2");

      var sectionContainer = document.createElement("div");
      var elements = document.createElement("div");

      section.className = "accordion";
      section.setAttribute("data-doc-type", self.schemaName);
      elements.className = "accordion-fields";

      icon.className = "icon toggle-icon drill-down-white";
      title.appendChild(icon);

      if (op) {
        title.appendChild(document.createTextNode(loc(op.name + "._group_label")));
      } else {
        title.appendChild(document.createTextNode(loc(self.schemaI18name + "._group_label")));
      }
      title.setAttribute("data-doc-id", self.docId);
      title.setAttribute("data-app-id", self.appId);
      title.onclick = accordion.click;
      if (self.schema.info.removable && !isDisabled(options) && authorizationModel.ok('remove-doc')) {
        $(title)
          .append($("<span>")
            .addClass("icon remove inline-right")
            .attr("data-test-class", "delete-schemas." + self.schemaName)
            .click(removeDoc));
      }

      if (self.schema.info.approvable) {
        elements.appendChild(self.makeApprovalButtons([], self.model));
      }

      sectionContainer.className = "accordion_content expanded";
      sectionContainer.setAttribute("data-accordion-state", "open");
      sectionContainer.id = "document-" + docId;

      appendElements(elements, self.schema, self.model, []);

      sectionContainer.appendChild(elements);
      section.appendChild(title);
      section.appendChild(sectionContainer);
      return section;
    }

    self.element = buildElement();
    validate();
    disableBasedOnOptions();
  };

  function updateOther(select) {
    var otherId = select.attr("data-select-other-id"),
        other = $("#" + otherId, select.parent().parent());
    other.parent().css("visibility", select.val() === "other" ? "visible" : "hidden");
  }

  function initSelectWithOther(i, e) { updateOther($(e)); }
  function selectWithOtherChanged() { updateOther($(this)); }

  function displayDocuments(containerSelector, application, documents, authorizationModel, options) {

    function getDocumentOrder(doc) {
      var num = doc.schema.info.order || 7;
      return num * 10000000000 + doc.created / 1000;
    }

    var sortedDocs = _.sortBy(documents, getDocumentOrder);

    var docgenDiv = $(containerSelector).empty();
    _.each(sortedDocs, function (doc) {
      var schema = doc.schema;

      docgenDiv.append(new DocModel(schema, doc.data, doc.meta, doc.id, application, authorizationModel, options).element);

      if (schema.info.repeating && !isDisabled(options) && authorizationModel.ok('create-doc')) {
        var btn = makeButton(schema.info.name + "_append_btn", loc(schema.info.name + "._append_label"));
        btn.className = "btn block";

        $(btn).click(function () {
          var self = this;
          ajax
            .command("create-doc", { schemaName: schema.info.name, id: application.id })
            .success(function (data) {
              var newDocId = data.doc;
              var newElem = new DocModel(schema, {}, {}, newDocId, application, authorizationModel).element;
              $(self).before(newElem);
            })
            .call();
        });
        docgenDiv.append(btn);
      }
    });

    $("select[data-select-other-id]", docgenDiv).each(initSelectWithOther).change(selectWithOtherChanged);
  }

  function isDisabled(options) { return options && options.disabled; }
  function doValidate(options) { return !options || options.validate; }

  return {
    displayDocuments: displayDocuments
  };

})();
