var DocModel = function(schema, doc, application, authorizationModel, options) {
  "use strict";

  var self = this;

  // Magic key: if schema contains "_selected" radioGroup,
  // user can select only one of the schemas named in "_selected" group
  var SELECT_ONE_OF_GROUP_KEY = "_selected";

  self.schema = schema;
  self.schemaName = schema.info.name;
  self.schemaI18name = schema.info.i18name;
  if (!self.schemaI18name) {
      self.schemaI18name = self.schemaName;
  }
  self.model = doc.data;
  self.meta = doc.meta;
  self.docId = doc.id;
  self.appId = application.id;
  self.application = application;
  self.authorizationModel = authorizationModel;
  self.eventData = { doc: doc.id, app: self.appId };
  self.propertyId = application.propertyId;
  self.isDisabled = options && options.disabled;
  self.events = [];

  self.subscriptions = [];

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

  self.sizeClasses = { "t": "form-input tiny", "s": "form-input short", "m": "form-input medium", "l": "form-input long"};

  // Context help
  self.addFocus = function (e) {
    var event = getEvent(e);
    var input$ = $(event.target);
    input$.focus();
  };

  self.findHelpElement = function (e) {
    var event = getEvent(e);
    var input$ = $(event.target);
    var help$ = input$.siblings(".form-help");
    if (!help$.length) {
      help$ = input$.parent().siblings(".form-help");
    }
    if (!help$.length) {
      return false;
    }
    return help$;
  };
  self.findErrorElement = function (e) {
    var event = getEvent(e);
    var input$ = $(event.target);
    var error$ = input$.siblings(".errorPanel");
    if (!error$.length) {
      error$ = input$.parent().siblings(".errorPanel");
    }
    if (!error$.length) {
      return false;
    }
    return error$;
  };

  self.showHelp = function (e) {
    var element = self.findHelpElement(e);
    if (element) {
      element.stop();
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
      element.stop();
      element.fadeOut("slow").css("display", "none");
    }
    self.hideError(e);
  };

  self.showError = function (e) {
    var element = self.findErrorElement(e);
    if (element && element.children && element.children().size()) {
      element.stop();
      element.fadeIn("slow").css("display", "block");
    }
  };

  self.hideError = function (e) {
    var element = self.findErrorElement(e);
    if (element) {
      element.stop();
      element.fadeOut("slow").css("display", "none");
    }
  };

  // trigger stored events once
  self.triggerEvents = function() {
    _.forEach(self.events, function(event) {
      hub.send(event.name, event.data);
    });
    self.events = [];
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

  // Option utils

  self.getCollection = function() {
    return (options && options.collection) ? options.collection : "documents";
  };

  function listen(subSchema, path, element) {
    _.forEach(subSchema.listen, function(listenEvent) {
      if (listenEvent === "filterByCode") {
        $(element).find("[data-codes]").addClass("hidden");
        self.subscriptions.push(hub.subscribe(listenEvent, function(event) {
          $(element).find("[data-codes]").addClass("hidden");
          $(element).find("[data-codes*='" + event.code + "']").removeClass("hidden");
        }));
      }
      if (listenEvent === "muutostapaChanged") {
        var prefix = _.dropRight(path.split("."));
        self.subscriptions.push(hub.subscribe({type: listenEvent, path: prefix.join(".")}, function(event) {
          $(element).prop("disabled", _.isEmpty(event.value));
        }));
      }
    });
  }

  function makeGroupHelpTextSpan(schema) {
    var span = document.createElement("span");
    span.className = "group-help-text";

    var locKey = schema["group-help"];
    if (locKey) {
      span.innerHTML = loc(locKey);
    }

    return span;
  }

  function makeSectionHelpTextSpan(schema) {
    var span = document.createElement("span");
    span.className = "group-help-text";
    var locKey = schema.info["section-help"];
    if (locKey) {
      span.innerHTML = loc(locKey);
    }

    return span;
  }

  function getUpdateCommand() {
    return (options && options.updateCommand) ? options.updateCommand : "update-doc";
  }

  // Element constructors
  // Supported options:
  // icon: icon class -> <button><i class="icon"></i><span>label</span></button>
  // className: class attribute value -> <button class="className">...
  function makeButton(id, label, opts) {
    opts = opts || {};
    var button = document.createElement("button");
    button.id = id;
    //button.className = "btn";
    if( opts.icon ) {
      var i = document.createElement( "i");
      i.setAttribute( "class", opts.icon );
      button.appendChild( i );
      var span = document.createElement( "span" );
      span.innerHTML = label;
      button.appendChild( span );
    } else {
      button.innerHTML = label;
    }
    if( opts.className ) {
      button.setAttribute( "class", opts.className );
    }
    return button;
  }

  function makeLabel(schema, type, pathStr, groupLabel) {
    var label = document.createElement("label");
    var path = groupLabel ? pathStr + "._group_label" : pathStr;

    var locKey = util.locKeyFromDocPath(self.schemaI18name + "." + path);
    if (schema.i18nkey) {
      locKey = schema.i18nkey;
    }

    var className = "form-label form-label-" + type;
    if (schema.labelclass) {
      className = className + " " + schema.labelclass;
    }

    label.id = pathStrToLabelID(pathStr);
    label.htmlFor = pathStrToID(pathStr);
    label.className = className;
    label.innerHTML = loc(locKey);
    return label;
  }

  function sourceValueChanged(input, value, sourceValue, localizedSourceValue) {
    if (sourceValue === value) {
      input.removeAttribute("title");
      $(input).removeClass("source-value-changed");
    } else if (sourceValue !== undefined && sourceValue !== value){
      $(input).addClass("source-value-changed");
      input.title = _.escapeHTML(loc("sourceValue") + ": " + (localizedSourceValue ? localizedSourceValue : sourceValue));
    }
  }

  function makeInput(type, pathStr, modelOrValue, subSchema) {
    var value = _.isObject(modelOrValue) ? getModelValue(modelOrValue, subSchema.name) : modelOrValue;
    var sourceValue = _.isObject(modelOrValue) ? getModelSourceValue(modelOrValue, subSchema.name) : undefined;
    var extraClass = self.sizeClasses[subSchema.size] || "";
    var readonly = subSchema.readonly;

    var input = document.createElement("input");
    input.id = pathStrToID(pathStr);
    input.name = self.docId + "." + pathStr;
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
      input.onchange = function(e) {
        if (type === "checkbox") {
          sourceValueChanged(input, input.checked, sourceValue, sourceValue ? loc("selected") : loc("notSelected"));
        } else {
          sourceValueChanged(input, input.value, sourceValue);
        }
        save(e, function() {
          if (subSchema) {
           emit(getEvent(e).target, subSchema);
          }
        });
      };
    }

    if (type === "checkbox") {
      input.checked = value;
      sourceValueChanged(input, value, sourceValue, sourceValue ? loc("selected") : loc("notSelected"));
    } else {
      input.value = value || "";
      sourceValueChanged(input, value, sourceValue);
    }
    return input;
  }

  function makeEntrySpan(subSchema, pathStr) {
    var help = null;
    var helpLocKey = util.locKeyFromDocPath(self.schemaI18name + "." + pathStr + ".help");
    if (subSchema.i18nkey) {
      helpLocKey = subSchema.i18nkey + ".help";
    }
    var span = document.createElement("span");
    var sizeClass = self.sizeClasses[subSchema.size] || "";
    span.className = "form-entry " + sizeClass;

    if (subSchema.codes) {
      span.setAttribute("data-codes", subSchema.codes.join(" "));
    }

    // Display text areas in a wide container
    if (subSchema.type === "text") {
      span.className = "form-entry form-full-width";
    }

    // Override style with layout option
    if (subSchema.layout) {
      span.className = "form-entry form-" + subSchema.layout + " " + sizeClass;
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

  self.statusOrder = function() {
    var stats = {};

    function update( name, selector, statusFun, approval ) {
      stats[name] = {fun: _.wrap(approval, statusFun),
                     approval: approval,
                     selector: selector
                    };
    }
    function setOrdered() {
      _( stats )
      .sortBy( _.partialRight( _.get, "approval.timestamp") )
      .pluck( 'fun' )
      .each( function( f ) {f()} )
      .value()
    }
    //  If name is null, every item's class is changed.
    // If cls is null, the approval class is used
    function setStatusClass( name, cls ) {
      var items = name ? [stats[name]] : stats;
      _.each( items, function( item ) {
        item.selector.removeClass( "approved rejected" );
        item.selector.addClass( cls ? cls : item.approval.value );
      })
    }

    function setSectionStatus( name, cls ) {
      var isApproved = cls === 'approved';
      var section = stats[name].selector.closest( "section");
      // Section is never rejected.
      section.toggleClass( "approved", isApproved );
      var bar = stats["bar"].selector;
      bar.removeClass( "approved rejected");
      bar.addClass( cls );
      bar.toggleClass( "positive", isApproved );
    }

    return {
      update: update,
      setOrdered: setOrdered,
      setStatusClass: setStatusClass,
      setSectionStatus: setSectionStatus
    }
  }();

  self.barStatusHandler = function( barDiv$, approval, text ) {
    self.statusOrder.update( 'bar', barDiv$, self.barStatusHandler, approval );
    var cls = approval.value;

    // Approving the whole approves the details.
    // Rejecting the whole resets the details. In other words
    // details should be intact after approving and immediately
    // rejecting the whole.
    self.statusOrder.setSectionStatus( 'bar', cls );
    // self.statusOrder.setStatusClass( 'bar',
    //                                  cls === 'approved' ? cls : null );

    //barDiv$.toggleClass( "positive", approval.value === "approved");
    barDiv$.children( "." + approval.value ).attr( "title", text );
  };

  // Bar denotes the accordion horizontal bar.
  // It is an object with two properties:
  //  selector: jQuery selector for bar.
  //  fun: function to be called when status changes.
  // Note: The bar's order name (see above) is fixed, because
  // it needs to be known by the detail status indicators.
  self.makeApprovalButtons = function (path, model, bar ) {
    var btnContainer$ = $("<span>").addClass("form-buttons");
    var statusContainer$ = null;
    if( !bar ) {
      statusContainer$ = $("<div>").addClass( "like-btn");
      statusContainer$.append( $("<i>").addClass( "lupicon-circle-attention rejected"));
      statusContainer$.append( $("<i>").addClass( "lupicon-circle-check approved"));
      statusContainer$.append( $("<span>"));
    }
    var approvalContainer$ = $("<span>").addClass("form-approval-status is-status empty").append(statusContainer$).append(btnContainer$);
    var approveButton$ = null;
    var rejectButton$ = null;
    var cmdArgs = { id: self.appId, doc: self.docId, path: path.join("."), collection: self.getCollection() };

    if (_.isEmpty(model)) {
      return approvalContainer$[0];
    }

    function setStatus(approval) {
      if (approval) {
        var text = loc(["document", approval.value]);
        if (approval.user && approval.timestamp) {
          text += " (" + approval.user.lastName + " " + approval.user.firstName;
          text += " " + moment(approval.timestamp).format("D.M.YYYY HH:mm") + ")";
        }
        if( bar ) {
          bar.fun( approval, text );
          self.statusOrder.update( 'bar', bar.selector, setStatus, approval );
        } else {
          statusContainer$.children("span").text(text);
          // statusContainer$.removeClass( "approved rejected");
          // statusContainer$.addClass( approval.value );
          var statusParent$ = statusContainer$.closest( ".is-status");
          self.statusOrder.update( statusParent$, statusParent$, setStatus, approval );
          self.statusOrder.setStatusClass( statusParent$, approval.value );
          if( approval.value === 'rejected') {
            self.statusOrder.setSectionStatus( statusParent$, 'rejected' );
          }
          // statusContainer$.removeClass (function(index, css) {
          //   return _.filter(css.split(" "), function(c) { return _.includes(c, "approval-"); }).join(" ");
          // });
          // statusContainer$.addClass ("approval-" + approval.value);
          // approvalContainer$.removeClass ("empty");
        }
      }
    }

    function makeApprovalButton(verb, noun, cssClass) {
      var cmd = verb + "-doc";
      var title = loc(["document", verb]);
      var opts = {className: cssClass + " " + (verb === "approve" ? "approved" : "rejected"),
                  icon: verb === "approve" ? "lupicon-check" : null};
      var button =
            $(makeButton(self.docId + "_" + verb, title, opts ))
          .click(function () {
            ajax.command(cmd, cmdArgs)
              .success(function () {
                // if (noun === "approved") {
                //   approveButton$.hide();
                //   rejectButton$.show();
                // } else {
                //   approveButton$.show();
                //   rejectButton$.hide();
                // }
                setStatus({ value: noun });
              })
              .call();
          });
      if (options && options.dataTestSpecifiers) {
        button.attr("data-test-id", verb + "-doc-" + self.schemaName);
      }
      return button;
    }

    function modelModifiedSince(model, timestamp) {
      if (model) {
        if (!timestamp) {
          return true;
        }
        if (!_.isObject(model)) {
          return false; // whitelist-action : "disabled"
        }
        if (_.has(model, "value")) {
          // Leaf
          return model.modified && model.modified > timestamp;
        }
        return _.find(model, function (myModel) { return modelModifiedSince(myModel, timestamp); });
      }
      return false;
    }

    var meta = self.getMeta(path);
    var approval = meta ? meta._approved : null;
    var requiresApproval = !approval || modelModifiedSince(model, approval.timestamp);
    // var allowApprove = requiresApproval || (approval && approval.value === "rejected");
    // var allowReject = requiresApproval || (approval && approval.value === "approved");


    if (self.authorizationModel.ok("approve-doc")) {
      approveButton$ = makeApprovalButton("approve", "approved", "positive");
      btnContainer$.append(approveButton$);

      // if (!allowApprove) {
      //     approveButton$.hide();
      // }
    }
    if (self.authorizationModel.ok("reject-doc")) {
      rejectButton$ = makeApprovalButton("reject", "rejected", "secondary");
      btnContainer$.append(rejectButton$);

    //   if (!allowReject) {
    //       rejectButton$.hide();
    //   }
    }

    // if (allowApprove || allowReject) {
    //     approvalContainer$.removeClass("empty");
    // }

    if( requiresApproval || approval ) {
      approvalContainer$.removeClass( "empty");
    }
    if (!requiresApproval) {
      setStatus(approval);
    }
    return approvalContainer$[0];
  };

  // Form field builders

  function buildCheckbox(subSchema, model, path) {
    var myPath = path.join(".");
    var span = makeEntrySpan(subSchema, myPath);
    var input = makeInput("checkbox", myPath, model, subSchema);
    input.onmouseover = self.showHelp;
    input.onmouseout = self.hideHelp;
    span.appendChild(input);

    $(input).prop("disabled", getModelDisabled(model, subSchema.name));

    if (subSchema.label) {
      var label = makeLabel(subSchema, "checkbox", myPath);
      label.onmouseover = self.showHelp;
      label.onmouseout = self.hideHelp;
      span.appendChild(label);
    }

    listen(subSchema, myPath, input);

    return span;
  }

  function setMaxLen(input, subSchema) {
    var maxLen = subSchema["max-len"] || 255; // if you change the default, change in model.clj, too
    input.setAttribute("maxlength", maxLen);
  }

  function buildString(subSchema, model, path, partOfChoice) {
    var myPath = path.join(".");
    var span = makeEntrySpan(subSchema, myPath);

    var supportedInputSubtypes = ["email", "time"];
    var inputType = (_.indexOf(supportedInputSubtypes, subSchema.subtype) > -1) ? subSchema.subtype : "text";

    var input = makeInput(inputType, myPath, model, subSchema);
    setMaxLen(input, subSchema);

    $(input).prop("disabled", getModelDisabled(model, subSchema.name));

    listen(subSchema, myPath, input);

    if (subSchema.label) {
      span.appendChild(makeLabel(subSchema, partOfChoice ? "string-choice" : "string", myPath));
    }

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
      unit.appendChild(document.createTextNode(loc(["unit", subSchema.unit])));

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
    return util.getIn(model, [name, "value"], "");
  }

  function getModelSourceValue(model, name) {
    return util.getIn(model, [name, "sourceValue"]);
  }

  function getModelDisabled(model, name) {
    return util.getIn(model, [name, "whitelist-action"]) === "disabled";
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

    input.className = "form-input textarea";
    var value = getModelValue(model, subSchema.name);
    input.value = value;
    var sourceValue = _.isObject(model) ? getModelSourceValue(model, subSchema.name) : undefined;

    sourceValueChanged(input, value, sourceValue);

    if (subSchema.readonly) {
      input.readOnly = true;
    } else {
      input.onchange = function(e) {
        sourceValueChanged(input, input.value, sourceValue);
        save(e);
      };
    }

    if (subSchema.label) {
      span.appendChild(makeLabel(subSchema, "text", myPath));
    }
    span.appendChild(input);
    return span;
  }

  function buildDate(subSchema, model, path) {
    var lang = loc.getCurrentLanguage();
    var myPath = path.join(".");
    var value = getModelValue(model, subSchema.name);

    var span = makeEntrySpan(subSchema, myPath);

    if (subSchema.label) {
      span.appendChild(makeLabel(subSchema, "date", myPath));
    }

    // date
    var input = $("<input>", {
      id: pathStrToID(myPath),
      name: self.docId + "." + myPath,
      type: "text",
      "class": "form-input text form-date",
      value: value
    });

    var sourceValue = getModelSourceValue(model, subSchema.name);

    sourceValueChanged(input.get(0), value, sourceValue);

    if (subSchema.readonly) {
      input.attr("readonly", true);
    } else {
      input.datepicker($.datepicker.regional[lang]).change(function(e) {
        sourceValueChanged(input.get(0), input.val(), sourceValue);
        save(e);
      });
    }
    input.appendTo(span);

    return span;
  }

  function buildTime(subSchema, model, path, partOfChoice) {
    // Set implisit options into a clone
    var timeSchema = _.clone(subSchema);
    timeSchema.subtype = "time";
    timeSchema.size = "m";
    timeSchema["max-len"] = 10; // hh:mm:ss.f
    return buildString(timeSchema, model, path, partOfChoice);
  }

  function buildSelect(subSchema, model, path) {
    var myPath = path.join(".");
    var select = document.createElement("select");
    // Set default value of "muutostapa" field to "Lisays" when adding a new huoneisto.
    if (subSchema.name === "muutostapa" && _.isEmpty(_.keys(model))) {
      model[subSchema.name] = {value: "lis\u00e4ys"};
    }

    $(select).prop("disabled", getModelDisabled(model, subSchema.name));

    var selectedOption = getModelValue(model, subSchema.name);
    var sourceValue = getModelSourceValue(model, subSchema.name);
    var span = makeEntrySpan(subSchema, myPath);
    var sizeClass = self.sizeClasses[subSchema.size] || "";

    select.onfocus = self.showHelp;
    select.onblur = self.hideHelp;
    select.onmouseover = self.showHelp;
    select.onmouseout = self.hideHelp;
    select.setAttribute("data-docgen-path", myPath);
    select.setAttribute("data-test-id", myPath);

    select.name = myPath;
    select.className = "form-input combobox " + (sizeClass || "");

    select.id = pathStrToID(myPath);

    var otherKey = subSchema["other-key"];
    if (otherKey) {
      var pathToOther = path.slice(0, -1);
      pathToOther.push(otherKey);
      select.setAttribute("data-select-other-id", pathStrToID(pathToOther.join(".")));
    }

    var option = document.createElement("option");
    option.value = "";
    option.appendChild(document.createTextNode(loc("selectone")));
    if (selectedOption === "") {
      option.selected = "selected";
    }
    select.appendChild(option);

    var options = _(subSchema.body)
      .map(function(e) {
        var locKey = self.schemaI18name + "." + myPath.replace(/\.\d+\./g, ".") + "." + e.name;
        if (e.i18nkey) {
          locKey = e.i18nkey;
        } else if (subSchema.i18nkey) {
          locKey = subSchema.i18nkey + "." + e.name;
        }
        return {
          name: e.name,
          locName: loc(locKey),
          disabled: e.disabled || false
        };
      })
      .sortBy(function(e) {
        if (subSchema.sortBy === "displayname") {
          return e.locName;
        }
        // lo-dash API doc tells that the sort is stable, so returning a static value equals to no sorting
        return 0;
      }).value();

    _.forEach(options, function(e) {
      var option = document.createElement("option");
      option.value = e.name;
      option.appendChild(document.createTextNode(e.locName));
      option.disabled = e.disabled;
      if (selectedOption === e.name) {
        option.selected = "selected";
      }
      select.appendChild(option);
    });

    if (otherKey) {
      option = document.createElement("option");
      option.value = "other";
      option.appendChild(document.createTextNode(loc("select-other")));
      if (selectedOption === "other") {
        option.selected = "selected";
      }
      select.appendChild(option);
    }

    var locSelectedOption = _.find(options, function(e) {
      return e.name === sourceValue;
    });

    sourceValueChanged(select, selectedOption, sourceValue, locSelectedOption ? locSelectedOption[1] : undefined);

    if (subSchema.readonly) {
      select.readOnly = true;
    } else {
      select.onchange = function(e) {
        sourceValueChanged(select, select.value, sourceValue, locSelectedOption ? locSelectedOption[1] : undefined);
        save(e);
        emit(getEvent(e).target, subSchema);
      };
    }

    listen(subSchema, myPath, select);

    emitLater(select, subSchema);

    if (subSchema.label) {
      span.appendChild(makeLabel(subSchema, "select", myPath, true));
    }
    span.appendChild(select);
    return span;
  }

  function buildGroup(subSchema, model, path, partOfChoice) {
    var myPath = path.join(".");
    var name = subSchema.name;
    var myModel = model[name] || {};
    var partsDiv = document.createElement("div");
    var div = document.createElement("div");

    appendElements(partsDiv, subSchema, myModel, path, save, partOfChoice);

    div.id = pathStrToGroupID(myPath);
    div.className = subSchema.layout === "vertical" ? "form-choice" : "form-group";

    var label = makeLabel(subSchema, "group", myPath, true);
    div.appendChild(label);

    var groupHelpText = makeGroupHelpTextSpan(subSchema);
    div.appendChild(groupHelpText);

    if (subSchema.approvable) {
      label.appendChild(self.makeApprovalButtons(path, myModel));
    }
    div.appendChild(partsDiv);

    listen(subSchema, myPath, div);
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
    partsDiv.className = subSchema.name + "-radioGroup";

    $.each(subSchema.body, function (i, o) {
      var pathForId = myPath + "." + o.name;
      var input = makeInput("radio", myPath, o.name, subSchema);
      input.id = pathStrToID(pathForId);
      input.checked = o.name === myModel;

      span.appendChild(input);
      if (subSchema.label) {
        span.appendChild(makeLabel(subSchema, "radio", pathForId));
      }
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
    span.className = "form-entry really-long";

    select.id = pathStrToID(myPath);

    select.name = myPath;
    select.className = "form-input combobox long";

    var otherKey = subSchema["other-key"];
    if (otherKey) {
      var pathToOther = path.slice(0, -1);
      pathToOther.push(otherKey);
      select.setAttribute("data-select-other-id", pathStrToID(pathToOther.join(".")));
    }

    if (subSchema.readonly) {
      select.readOnly = true;
    } else {
      select.onchange = function (e) {
        var event = getEvent(e);
        var target = event.target;

        var buildingId = target.value;

        function mergeFromWfs(overwriteWithBackendData) {
          ajax
          .command("merge-details-from-krysp", {
            id: self.appId, documentId: self.docId,
            path: myPath,
            buildingId: buildingId,
            overwrite: overwriteWithBackendData,
            collection: self.getCollection()
          })
          .success(_.partial(repository.load, self.appId, _.noop))
          .onError("error.no-legacy-available", function(e) {
            notify.error(loc(e.text));
          })
          .call();
        }

        if (buildingId !== "" && buildingId !== "other") {
          LUPAPISTE.ModalDialog.showDynamicYesNo(
              loc("overwrite.confirm"),
              loc("application.building.merge"),
              {title: loc("yes"), fn: _.partial(mergeFromWfs, true)},
              {title: loc("no"), fn: _.partial(mergeFromWfs, false)}
          );
        } else {
          mergeFromWfs(true);
        }
        return false;
      };
    }

    option.value = "";
    option.appendChild(document.createTextNode(loc("selectone")));
    if (selectedOption === "") {
      option.selected = "selected";
    }
    select.appendChild(option);

    var otherOption = null;
    if (otherKey) {
      otherOption = document.createElement("option");
      otherOption.value = "other";
      otherOption.appendChild(document.createTextNode(loc("select-other")));
      if (selectedOption === "other") {
        otherOption.selected = "selected";
      }
      select.appendChild(otherOption);
    }

    ajax
      .command("get-building-info-from-wfs", { id: self.appId })
      .success(function (data) {
        _.each(data.data, function (building) {
          var name = building.buildingId;
          var usage = building.usage;
          var created = building.created;
          var option = document.createElement("option");
          option.value = name;
          option.appendChild(document.createTextNode(name + " (" + usage + ") - " + created));
          if (selectedOption === name) {
            option.selected = "selected";
          }
          if (otherOption) {
            select.insertBefore(option, otherOption);
          } else {
            select.appendChild(option);
          }
        });
      })
      .error(function (e) {
        var error = e.text ? e.text : "error.unknown";
        var option = document.createElement("option");
        option.value = name;
        option.appendChild(document.createTextNode(loc(error)));
        option.selected = "selected";
        select.appendChild(option);
        select.setAttribute("disabled", true);
      })
      .call();

    if (subSchema.label) {
      span.appendChild(makeLabel(subSchema, "select", myPath));
    }
    span.appendChild(select);
    return span;
  }

  function buildNewBuildingSelector(subSchema, model, path) {
    var myPath = path.join(".");
    var select = document.createElement("select");
    var selectedOption = getModelValue(model, subSchema.name);
    var option = document.createElement("option");
    var span = makeEntrySpan(subSchema, myPath);
    span.className = "form-entry really-long";

    select.id = pathStrToID(myPath);

    select.name = myPath;
    select.className = "form-input combobox long";

    if (subSchema.readonly) {
      select.readOnly = true;
    } else {
      select.onchange = function() {
        var target = select;
        var indicator = createIndicator(target);
        var path = target.name;
        var label = document.getElementById(pathStrToLabelID(path));
        var loader = loaderImg();
        var basePathEnd = (path.lastIndexOf(".") > 0) ? path.lastIndexOf(".") : path.length;
        var basePath = path.substring(0, basePathEnd);

        var option$ = $(target[target.selectedIndex]);
        var index = option$.val();
        var propertyId = option$.attr("data-propertyid") || "";
        var buildingId = option$.attr("data-buildingid") || "";
        var nationalId = option$.attr("data-nationalid") || (buildingId.length === 10 ? buildingId : "");
        var localShortId = option$.attr("data-localshortid") || (buildingId.length === 3 ? buildingId : "");
        var localId = option$.attr("data-localid") || "";

        var paths = [basePath + ".jarjestysnumero", basePath + ".kiinttun", basePath + ".rakennusnro", basePath + ".valtakunnallinenNumero", basePath + ".kunnanSisainenPysyvaRakennusnumero"];
        var values = [index, propertyId, localShortId, nationalId, localId];

        if (label) {
          label.appendChild(loader);
        }

        saveForReal(paths, values, _.partial(afterSave, label, loader, indicator, null));
        return false;
      };
    }

    option.value = "";
    option.appendChild(document.createTextNode(loc("selectone")));
    if (selectedOption === "") {
      option.selected = "selected";
    }
    select.appendChild(option);

    $.each(self.application.buildings, function (i, building) {
          var name = building.index;
          var option = document.createElement("option");
          option.value = name;
          option.setAttribute("data-propertyid", building.propertyId || "");
          option.setAttribute("data-buildingid", building.buildingId || "");
          option.setAttribute("data-localshortid", building.localShortId || "");
          option.setAttribute("data-nationalid", building.nationalId || "");
          option.setAttribute("data-localid", building.localId || "");
          option.appendChild(document.createTextNode(util.buildingName(building)));
          if (selectedOption === name) {
            option.selected = "selected";
          }
          select.appendChild(option);
        });

    span.appendChild(makeLabel(subSchema, "select", myPath));
    span.appendChild(select);
    return span;
  }

  function paramsStr(params) {
    return _.map(_.keys(params), function(key) {
      return key + ": " + key;
    }).join(", ");
  }

  function createComponent(name, params, classes) {
    // createElement works with IE8
    var element = document.createElement(name);
    $(element)
      .attr("params", paramsStr(params))
      .addClass(classes)
      .applyBindings(params);
    return element;
  }

  function buildForemanHistory(subSchema, model, path) {
    var params = {
      applicationId: self.appId
    };
    return createComponent("foreman-history", params, "form-table");
  }

  function buildForemanOtherApplications(subSchema, model, path, partOfChoice) {
    var params = {
      applicationId: self.appId,
      documentId: self.docId,
      documentName: self.schemaName,
      hetu: undefined,
      model: model[subSchema.name] || {},
      subSchema: subSchema,
      path: path,
      schemaI18name: self.schemaI18name,
      partOfChoice: partOfChoice,
      validationErrors: doc.validationErrors
    };

    return createComponent("foreman-other-applications", params, "form-table");
  }

  function buildFillMyInfoButton(subSchema, model, path) {
    if (model.fillMyInfo && model.fillMyInfo.disabled) {
      return;
    }

    var myNs = path.slice(0, path.length - 1).join(".");

    var params = {
      id: self.appId,
      documentId: self.docId,
      documentName: self.schemaName,
      path: myNs,
      collection: self.getCollection()
    };

    return createComponent("fill-info", params);
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
    if (authorizationModel.ok("set-user-to-document")) {
      select.onchange = function (e) {
        var event = getEvent(e);
        var target = event.target;
        var userId = target.value;
        if (!_.isEmpty(userId)) {
          ajax
            .command("set-user-to-document", { id: self.appId, documentId: self.docId, userId: userId, path: myNs, collection: self.getCollection() })
            .success(function () {
              save(event, function () { repository.load(self.appId); });
            })
            .error(function(e) {
              if (e.text !== "error.application-does-not-have-given-auth") {
                error("Failed to set user to document", userId, self.docId, e);
              }
              notify.error(loc("error.dialog.title"), loc(e.text));
            })
            .call();
        }
        return false;
      };
    } else {
      select.disabled = true;
    }
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
        option.appendChild(document.createTextNode(user.lastName + " " + user.firstName));
        if (selectedOption === value) {
          option.selected = "selected";
        }
        if (user.invite) {
          option.disabled = true;
        }
        select.appendChild(option);
      }
    });

    var label = document.createElement("label");
    var locKey = ("person-selector");
    label.className = "form-label form-label-select";
    label.innerHTML = loc(locKey);
    span.appendChild(label);
    span.appendChild(select);

    // new invite
    if (authorizationModel.ok("invite-with-role")) {
      var button =
        $("<button>", {
          "class": "icon-remove positive",
          text: loc("personSelector.invite"),
          click: function () {
            $("#invite-document-name").val(self.schemaName).change();
            $("#invite-document-path").val(myNs).change();
            $("#invite-document-id").val(self.docId).change();
            LUPAPISTE.ModalDialog.open("#dialog-valtuutus");
            return false;
          }
        });
      if (options && options.dataTestSpecifiers) {
        button.attr("data-test-id", "application-invite-" + self.schemaName);
      }
      button.appendTo(span);
    }

    return span;
  }

  function buildCompanySelector(subSchema, model, path) {
    var myNs = path.slice(0, path.length - 1).join(".");

    var params = {
      id: self.appId,
      documentId: self.docId,
      documentName: self.schemaName,
      path: myNs,
      collection: self.getCollection(),
      selected: getModelValue(model, subSchema.name),
      schema: subSchema
    };

    var span = makeEntrySpan(subSchema, path.join("."));
    span.appendChild(createComponent("company-selector", params));
    $(span).addClass("companySelector");
    return span;
  }

  function buildTableRow(subSchema, model, path, partOfChoice) {
    var myPath = path.join(".");
    var name = subSchema.name;
    var myModel = model[name] || {};
    var row = document.createElement("tr");
    appendElements(row, subSchema, myModel, path, save, partOfChoice);

    row.id = pathStrToGroupID(myPath);
    return row;
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
    hetu: buildString,
    text: buildText,
    checkbox: buildCheckbox,
    select: buildSelect,
    radioGroup: buildRadioGroup,
    date: buildDate,
    time: buildTime,
    element: buildElement,
    buildingSelector: buildBuildingSelector,
    newBuildingSelector: buildNewBuildingSelector,
    fillMyInfoButton: buildFillMyInfoButton,
    foremanHistory: buildForemanHistory,
    foremanOtherApplications: buildForemanOtherApplications,
    personSelector: buildPersonSelector,
    companySelector: buildCompanySelector,
    table: buildTableRow,
    unknown: buildUnknown
  };

  function removeData(id, doc, path) {
    ajax
      .command("remove-document-data", { doc: doc, id: id, path: path, collection: self.getCollection() })
      .success(function () {
        repository.load(id);
      })
      .call();
  }

  function build(subSchema, model, path, partOfChoice) {
    // Do not create hidden whitelisted elements
    var whitelistedRoles = util.getIn(subSchema, ["whitelist", "roles"]);
    var schemaBranchHidden = util.getIn(subSchema, ["whitelist", "otherwise"]) === "hidden" && !_.contains(whitelistedRoles, lupapisteApp.models.currentUser.role());
    var schemaLeafHidden = util.getIn(model, [subSchema.name, "whitelist"]) === "hidden";

    if (subSchema.hidden || schemaLeafHidden || schemaBranchHidden) {
      return;
    }

    if (subSchema.label === undefined) {
      subSchema.label = true;
    }

    var myName = subSchema.name;
    var myPath = path.concat([myName]);
    var builder = builders[subSchema.type] || buildUnknown;
    var repeatingId = myPath.join("-");

    function makeElem(myModel, id) {
      var elem = builder(subSchema, myModel, myPath.concat([id]), partOfChoice);
      if (elem) {
        elem.setAttribute("data-repeating-id", repeatingId);
        elem.setAttribute("data-repeating-id-" + repeatingId, id);

        if (subSchema.repeating && !self.isDisabled && authorizationModel.ok("remove-document-data")) {
          var removeButton = document.createElement("span");
          removeButton.className = "icon remove-grey inline-right";
          removeButton.onclick = function () {
            LUPAPISTE.ModalDialog.showDynamicYesNo(loc("document.delete.header"), loc("document.delete.message"),
                { title: loc("yes"), fn: function () { removeData(self.appId, self.docId, myPath.concat([id])); } },
                { title: loc("no") });
          };
          if (options && options.dataTestSpecifiers) {
            removeButton.setAttribute("data-test-class", "delete-schemas." + subSchema.name);
          }
          if (subSchema.type === "table") {
            var td = document.createElement("td");
            td.appendChild(removeButton);
            elem.appendChild(td, elem.childNodes[0]);
          } else {
            elem.insertBefore(removeButton, elem.childNodes[0]);
          }
        }
      }
      return elem;
    }

    function buildElements(models) {
      return _.map(models, function (val, key) {
        var myModel = {};
        myModel[myName] = val;
        return makeElem(myModel, key);
      });
    }

    function createTableHeader(models, pathStr) {
      var thead = document.createElement("thead");
      var tr = document.createElement("tr");
      _.each(subSchema.body, function(item) {
        var locKey = util.locKeyFromDocPath(self.schemaI18name + "." + pathStr + "." + item.name);
        if (item.i18nkey) {
          locKey = item.i18nkey;
        }
        var th = document.createElement("th");
        th.innerHTML = loc(locKey);
        tr.appendChild(th);
      });
      // remove button column
      tr.appendChild(document.createElement("th"));
      thead.appendChild(tr);
      return thead;
    }

    if (subSchema.repeating && !subSchema.uicomponent) {
      var models = model[myName];
      if (!models) {
          models = subSchema.initiallyEmpty ? [] : [{}];
      }

      var elements;

      if (subSchema.type === "table") {
        elements = buildElements(models);
        var div = document.createElement("div");
        div.className = "form-table";
        var table = document.createElement("table");
        table.id = "table-" + subSchema.name;
        var tbody = document.createElement("tbody");
        table.appendChild(createTableHeader(models, myPath.join(".")));
        _.each(elements, function(element) {
          tbody.appendChild(element);
        });
        table.appendChild(tbody);

        var label = makeLabel(subSchema, "table", myPath.join("."), true);
        div.appendChild(label);

        var groupHelpText = makeGroupHelpTextSpan(subSchema);
        div.appendChild(groupHelpText);

        if (subSchema.approvable) {
          div.appendChild(self.makeApprovalButtons(path, models));
        }
        div.appendChild(table);

        elements = [div];
      } else {
        elements = buildElements(models);
      }

      var appendButton = makeButton(myPath.join("_") + "_append",
                                    loc([self.schemaI18name, myPath.join("."), "_append_label"]),
                                    {icon: "lupicon-circle-plus", className: "secondary"});

      var appender = function () {
        var parent$ = $(this).closest(".accordion-fields");
        var count = parent$.find("*[data-repeating-id='" + repeatingId + "']").length;
        while (parent$.find("*[data-repeating-id-" + repeatingId + "='" + count + "']").length) {
          count++;
        }
        var myModel = {};
        myModel[myName] = {};
        $(this).before(makeElem(myModel, count));
      };

      var tableAppender = function () {
        var parent$ = $(this).closest(".accordion-fields").find("#" + "table-" + subSchema.name + " tbody");
        var count = parent$.find("*[data-repeating-id='" + repeatingId + "']").length;
        while (parent$.find("*[data-repeating-id-" + repeatingId + "='" + count + "']").length) {
          count++;
        }
        var myModel = {};
        myModel[myName] = {};
        parent$.append(makeElem(myModel, count));
      };

      var copyElement = function() {
        var parent$ = $(this).closest(".accordion-fields").find("tbody");
        var count = parent$.find("*[data-repeating-id='" + repeatingId + "']").length;
        while (parent$.find("*[data-repeating-id-" + repeatingId + "='" + count + "']").length) {
          count++;
        }
        var lastItem$ = parent$.find("tr").last();

        var myModel = {};
        myModel[myName] = {};
        var newItem = makeElem(myModel, count);

        // copy last element items to new
        lastItem$.find("td").each(function(index) {
          var newInput$ = $($(newItem).find("input, select")[index]);
          var oldInput$ = $(this).find("input, select");
          var prop = "value";
          if(oldInput$.is(":checkbox")) {
            prop = "checked";
          }
          var oldValue = oldInput$.prop(prop);
          if(oldValue) {
            newInput$.prop(prop, oldInput$.prop(prop));
            newInput$.change();
          }
        });

        parent$.append(newItem);
      };

      var buttonGroup = document.createElement("div");
      buttonGroup.className = "button-group";
      buttonGroup.appendChild(appendButton);

      if (subSchema.type === "table") {
        $(appendButton).click(tableAppender);
        var locKey = [self.schemaI18name, myPath.join("."), "copyLabel"];
        if (subSchema.copybutton) {
          if (subSchema.i18nkey) {
            locKey = [subSchema.i18nkey, "copyLabel"];
          }
          var copyButton = makeButton(myPath.join("_") + "_copy", loc(locKey),
                                      {icon: "lupicon-circle-plus", className: "secondary"});
          $(copyButton).click(copyElement);
          buttonGroup.appendChild(copyButton);
        }
      } else {
        $(appendButton).click(appender);
      }

      elements.push(buttonGroup);
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
        if (elem) {
          // TODO can't really detect table cell from label key value
          if (!subSchema.label) {
            var td = document.createElement("td");
            td.appendChild(elem);
            elem = td;
          }
          body.appendChild(elem);
        }
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

  function saveForReal(paths, values, callback) {
    var p = _.isArray(paths) ? paths : [paths];
    var v = _.isArray(values) ? values : [values];
    var updates = _.zip(
        _.map(p, function(path) {return path.replace(new RegExp("^" + self.docId + "."), "");}),
        v);
    var updateCommand = getUpdateCommand();

    ajax
      .command(updateCommand, { doc: self.docId, id: self.appId, updates: updates, collection: self.getCollection() })
      // Server returns empty array (all ok), or array containing an array with three
      // elements: [key status message]. Here we use just the status.
      .success(function (e) {
        var status = (e.results.length === 0) ? "ok" : e.results[0].result[0];
        callback(updateCommand, status, e.results);
      })
      .error(function (e) { error(e); callback(updateCommand, "err"); })
      .fail(function (e) { error(e); callback(updateCommand, "err"); })
      .call();
  }

  self.showValidationResults = function(results) {
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
          errorPanel$.append("<span class='" + level + "'>" + loc(["error", code]) + "</span>");
        }

        $("#" + pathStrToLabelID(pathStr)).addClass(level);
        $("#" + pathStrToID(pathStr)).addClass(level);
      });
    }
  };

  function validate() {
    if (!options || options.validate) {
      ajax
        .query("validate-doc", { id: self.appId, doc: self.docId, collection: self.getCollection() })
        .success(function (e) { self.showValidationResults(e.results); })
        .call();
    }
  }

  function disableBasedOnOptions() {
    if (!self.authorizationModel.ok(getUpdateCommand()) || options && options.disabled) {
      $(self.element).find("input, textarea").attr("readonly", true).unbind("focus");
      $(self.element).find("select, input[type=checkbox], input[type=radio]").attr("disabled", true);
      $(self.element).find("button").hide();
    }
  }

  function createIndicator(eventTarget, className) {
    className = className || "form-indicator";
    var parent$ = $(eventTarget.parentNode);
    parent$.find("." + className).remove();
    var indicator = document.createElement("span");
    var icon = document.createElement("span");
    var text = document.createElement("span");
    text.className = "text";
    icon.className = "icon";
    indicator.className = className;
    indicator.appendChild(text);
    indicator.appendChild(icon);
    parent$.append(indicator);
    return indicator;
  }

  function showIndicator(indicator, className, locKey) {
    var parent$ = $(indicator).closest("table");
    var i$ = $(indicator);

    if(parent$.length > 0) {
      // disable indicator text for table element
      i$.addClass(className).fadeIn(200);
    } else {
      i$.children(".text").text(loc(locKey));
      i$.addClass(className).fadeIn(200);
    }

    setTimeout(function () {
      i$.removeClass(className).fadeOut(200, function () {});
    }, 4000);
  }

  function afterSave(label, loader, indicator, callback, updateCommand, status, results) {
    self.showValidationResults(results);
    if (label) {
      label.removeChild(loader);
    }
    if (status === "warn" || status === "tip") {
      showIndicator(indicator, "form-input-saved", "form.saved");
    } else if (status === "err") {
      showIndicator(indicator, "form-input-err", "form.err");
    } else if (status === "ok") {
      showIndicator(indicator, "form-input-saved", "form.saved");
    } else if (status !== "ok") {
      error("Unknown status:", status);
    }

    // Send updated event
    var eventType = updateCommand + "-success";
    hub.send(eventType, {appId: self.appId, documentId: self.docId, status: status, results: results});


    if (callback) { callback(); }
    // No return value or stopping the event propagation:
    // That would prevent moving to the next field with tab key in IE8.
  }

  function save(e, callback) {
    var event = getEvent(e);
    var target = event.target;
    var indicator = createIndicator(target);

    var path = target.name;
    var loader = loaderImg();

    var value = target.value;
    if (target.type === "checkbox") {
      value = target.checked;
    }

    var label = document.getElementById(pathStrToLabelID(path));
    if (label) {
      label.appendChild(loader);
    }

    saveForReal(path, value, _.partial(afterSave, label, loader, indicator, callback));
  }

  var emitters = {
    filterByCode: function(event, value, path, subSchema, sendLater) {
      var schemaValue = _.find(subSchema.body, {name: value});
      var code = schemaValue ? schemaValue.code : "";
      if (sendLater) {
        self.events.push({name: event, data: {code: code}});
      } else {
        hub.send(event, {code: code});
      }
    },
    hetuChanged: function(event, value) {
      hub.send(event, {value: value});
    },
    muutostapaChanged: function(event, value, path) {
      var prefix = _.dropRight(path.split("."));
      hub.send(event, {path: prefix.join("."), value: value});
    },
    emitUnknown: function(event) {
      error("Unknown emitter event:", event);
    }
  };

  function emit(target, subSchema, sendLater) {
    if (subSchema.emit) {
      var value = target.value;
      var path = $(target).attr("data-docgen-path");
      _.forEach(subSchema.emit, function(event) {
        var emitter = emitters[event] || emitters.emitUnknown;
        emitter(event, value, path, subSchema, sendLater);
      });
    }
  }

  function emitLater(target, subSchema) {
    emit(target, subSchema, true);
  }

  function removeDoc(e) {
    var n$ = $(e.target).parent();
    while (!n$.is("section")) {
      n$ = n$.parent();
    }
    var op = self.schema.info.op;

    var documentName = "";
    if (op) {
      documentName = loc([op.name, "_group_label"]);
    } else {
      documentName = loc([self.schema.info.name, "_group_label"]);
    }

    function onRemovalConfirmed() {
      ajax.command("remove-doc", { id: self.appId, docId: self.docId, collection: self.getCollection() })
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

  function buildDescriptionElement(operation) {
    var wrapper = document.createElement("span");
    var descriptionSpan = document.createElement("span");
    var description = document.createTextNode("");
    var descriptionInput = document.createElement("input");
    var iconSpanWrapper = document.createElement("span");
    var iconSpan = document.createElement("span");
    var iconTextSpan = document.createElement("span");
    iconTextSpan.appendChild(document.createTextNode(loc("op-description.edit")));

    // test ids
    if (options && options.dataTestSpecifiers) {
      descriptionSpan.setAttribute("data-test-id", "op-description");
      iconSpanWrapper.setAttribute("data-test-id", "edit-op-description");
      descriptionInput.setAttribute("data-test-id", "op-description-editor");
    }

    wrapper.className = "op-description-wrapper";
    descriptionSpan.className = "op-description";
    if (operation.description) {
      description.nodeValue = operation.description;
      descriptionInput.value = operation.description;
      $(iconTextSpan).addClass("hidden");
    } else if (authorizationModel.ok("update-op-description")) {
      iconSpanWrapper.appendChild(iconTextSpan);
    }

    wrapper.onclick = function(e) {
      var event = getEvent(e);
      // Prevent collapsing accordion when input is clicked
      event.stopPropagation();
    };

    descriptionInput.type = "text";
    descriptionInput.className = "accordion-input text hidden";

    var saveInput = _.debounce(function() {
      $(descriptionInput).off("blur");
      var value = _.trim(descriptionInput.value);
      if (value === "") {
        value = null;
        iconSpanWrapper.appendChild(iconTextSpan);
        $(iconTextSpan).removeClass("hidden");
      }

      ajax.command("update-op-description", {id: self.appId, "op-id": operation.id, desc: value })
        .success(function() {
          var indicator = createIndicator(descriptionInput, "accordion-indicator");
          showIndicator(indicator, "accordion-input-saved", "form.saved");
          hub.send("op-description-changed", {appId: self.appId, "op-id": operation.id, "op-desc": value});
        })
        .call();

      description.nodeValue = descriptionInput.value;
      $(descriptionInput).addClass("hidden");
      $(iconSpanWrapper).removeClass("hidden");
      $(descriptionSpan).removeClass("hidden");
    }, 250);

    descriptionInput.onfocus = function() {
      descriptionInput.onblur = function() {
        saveInput();
      };
    };

    descriptionInput.onkeyup = function(e) {
      // trigger save on enter and esc keypress
      var event = getEvent(e);
      event.stopPropagation();
      if (event.keyCode === 13 || event.keyCode === 27) {
        $(descriptionInput).off("blur");
        descriptionInput.blur();
        saveInput();
      }
    };

    if (authorizationModel.ok("update-op-description")) { // don't provide edit icon

      iconSpan.className = "icon edit";
      iconSpanWrapper.onclick = function(e) {
        var event = getEvent(e);
        event.stopPropagation();

        if (iconSpanWrapper.contains(iconTextSpan)) {
          $(iconTextSpan).addClass("hidden");
        }

        $(iconSpanWrapper).addClass("hidden");
        $(descriptionSpan).addClass("hidden");
        $(descriptionInput).removeClass("hidden");
        descriptionInput.focus();
      };

      iconSpanWrapper.appendChild(iconSpan);
      iconSpanWrapper.appendChild(iconTextSpan);
    }

    descriptionSpan.appendChild(description);
    wrapper.appendChild(descriptionSpan);
    wrapper.appendChild(descriptionInput);
    wrapper.appendChild(iconSpanWrapper);
    return wrapper;
  }

  function buildElement() {
    var op = self.schema.info.op;

    var section = document.createElement("section");
    var iconUp = document.createElement("i");
    var iconDown = document.createElement("i");
    var toggle = document.createElement("button");
    var title = document.createElement( "span");
    var iconRejected = $("<i>").addClass( "lupicon-circle-attention rejected");
    var iconApproved = $("<i>").addClass( "lupicon-circle-check approved");

    var sectionContainer = document.createElement("div");
    var elements = document.createElement("div");

    var accordionCollapsed = (options && options.accordionCollapsed) ? true : false;

    section.className = "accordion";
    section.setAttribute("data-doc-type", self.schemaName);
    elements.className = "accordion-fields";

    iconUp.className = "lupicon-chevron-up toggle";
    iconDown.className = "lupicon-chevron-down";
    toggle.appendChild( iconDown );
    toggle.appendChild( iconUp );
    toggle.className = "sticky secondary accordion-toggle is-status";
    toggle.appendChild( title );
    var icons = $("<span>").addClass( "icons").append( iconRejected ).append( iconApproved );
    $(toggle).append( icons );

    title.setAttribute("data-doc-id", self.docId);
    title.setAttribute("data-app-id", self.appId);
    title.className = "title";
    toggle.onclick = accordion.click;
    var docId = util.getIn(self, ["schema", "info", "op", "id"]);
    var notPrimaryOperation = (!docId || docId !== util.getIn(self.application, ["primaryOperation", "id"]));

    var isSecondaryOperation = (docId && !_.isEmpty(_.where(self.application.secondaryOperations, {id: docId})));

    if (self.schema.info.removable && !self.isDisabled && authorizationModel.ok("remove-doc") && notPrimaryOperation) {
      var removeSpan =
        $("<span>")
          .addClass("icon remove inline-right")
          .click(removeDoc);
      if (options && options.dataTestSpecifiers) {
        removeSpan.attr("data-test-class", "delete-schemas." + self.schemaName);
      }
      $(title).append(removeSpan);
    }

    var operationType = document.createElement("span");
    if (!notPrimaryOperation) {
      operationType.className = "icon star-selected";
      operationType.title = loc("operations.primary");
      operationType.setAttribute("data-op-name", op.name);
      elements.appendChild(operationType);
    }

    if (isSecondaryOperation) {
      operationType.className = "icon star-unselected";
      operationType.title = loc("operations.primary.select");
      $(operationType)
      .click(function() {
        ajax.command("change-primary-operation", {id: self.appId, secondaryOperationId: docId})
        .success(function() {
          repository.load(self.appId);
        })
        .call();
        return false;
      });
      operationType.setAttribute("data-op-name", op.name);
      elements.appendChild(operationType);
    }

    if (self.schema.info.approvable) {
      elements.appendChild(self.makeApprovalButtons([],
                                                    self.model,
                                                    {fun:  _.partial( self.barStatusHandler, $(toggle)),
                                                     selector: $(toggle)
                                                    }));
    }

    if (op) {
      title.appendChild(document.createTextNode(loc([op.name, "_group_label"])));
      elements.appendChild(buildDescriptionElement(op));
    } else {
      title.appendChild(document.createTextNode(loc([self.schema.info.name, "_group_label"])));
    }

    sectionContainer.className = "accordion_content" + (accordionCollapsed ? "" : " expanded");
    sectionContainer.setAttribute("data-accordion-state", (accordionCollapsed ? "closed" : "open"));
    sectionContainer.id = "document-" + self.docId;

    var sectionHelpText = makeSectionHelpTextSpan(self.schema);
    sectionContainer.appendChild(sectionHelpText);

    appendElements(elements, self.schema, self.model, []);

    sectionContainer.appendChild(elements);
    section.appendChild(toggle);
    section.appendChild(sectionContainer);
    self.statusOrder.setOrdered();
    return section;
  }

  hub.subscribe("application-loaded", function() {
    while (self.subscriptions.length > 0) {
      hub.unsubscribe(self.subscriptions.pop());
    }
  }, true);

  self.element = buildElement();
  // If doc.validationErrors is truthy, i.e. doc includes ready evaluated errors,
  // self.showValidationResults is called with in docgen.js after this docmodel has been appended to DOM.
  // So self.showValidationResults cannot be called here because of its jQuery lookups.
  if (!doc.validationErrors) {
    validate();
  }
  disableBasedOnOptions();

};
