var DocModel = function(schema, doc, application, authorizationModel, options) {
  "use strict";

  var self = this;

  self.schema = schema;
  self.schemaName = schema.info.name;
  self.schemaI18name = schema.info.i18name;
  if (!self.schemaI18name) {
      self.schemaI18name = self.schemaName;
  }
  self.model = doc.data;
  self.meta = doc.meta;
  self.docId = doc.id;
  self.docDisabled = doc.disabled;
  self.docPostVerdictEdit = false;
  self.appId = application.id;
  self.application = application;
  self.authorizationModel = authorizationModel;
  self.eventData = { doc: doc.id, app: self.appId };
  self.propertyId = application.propertyId;
  self.propertyIdSource = application.propertyIdSource;
  self.isDisabled = (options && options.disabled) || self.docDisabled;
  self.partiesModel = options.partiesModel;
  self.events = [];

  self.subscriptions = [];

  var flagsService = lupapisteApp.services.schemaFlagsService;

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
      return self.getMeta( _.tail( path ), val );
    }
  };

  self.sizeClasses = { "t": "tiny", "s": "short", "m": "medium", "l": "long", "xl": "really-long"};

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
    });
  }

  // ----------------------------------------------------------------------
  // Approval and related utilities. Used by group-approval and
  // section components.
  // Note: approval arguments are functions.
  //       In practise, they are observables.

  function approvalCommand( cmd, path, cb, extraParams ) {
    cb = cb || _.noop;
    ajax.command( cmd,
                  _.merge( {id: self.appId,
                            doc: self.docId,
                            path: path.join("."),
                            collection: self.getCollection()},
                           extraParams ))
      .success( function( result ) {
      cb( result.approval );
      if (cmd !== "reject-doc-note") {
        self.approvalHubSend( result.approval, path );
      }
      window.Stickyfill.rebuild();
    })
    .call();
  }

  // Updates approval status in the backend.
  // path: approval path
  // flag: true is approved, false rejected.
  // cb: callback function to be called on success.
  self.updateApproval = function( path, flag, cb ) {
    approvalCommand( sprintf( "%s-doc", flag ? "approve" : "reject"),
                     path,
                     cb );
  };

  self.updateRejectNote = function( path, note, cb ) {
    approvalCommand( "reject-doc-note", path, cb, {note: note || ""});
  };

  // Returns the latest modification time of the model or
  // zero if no modifications.
  function modelTimestamp( model ) {
    if( _.isObject( model )) {
      return !_.isUndefined(model.value)
                ? model.modified || 0
                : _.spread(Math.max)(_.map( model, modelTimestamp));
    }
    return 0;
  }

  // Approval is current if it has a value that is newer than
  // than latest model change.
  self.isApprovalCurrent = function( approvalModel, approvalFun ) {
    var approval = approvalFun();
    return approval && approval.timestamp > modelTimestamp( approvalModel );
  };

  // Returns always "core" approval object (value, timestamp properties.)
  // If approvalFun does not yield correct, up-to-date approval then
  // NEUTRAL with the latest model timestamp is returned.
  self.safeApproval = function( approvalModel, approvalFun) {

    var approval = approvalFun();
    var ts = modelTimestamp( approvalModel );
    return approval && approval.timestamp > ts
         ? approval
         : {value: "neutral", timestamp: ts};
  };

  self.approvalHubSubscribe = function(fun, listenBroadcasts) {
    var filter = {eventType: "approval-status",
                  docId: self.docId,
                  broadcast: Boolean(listenBroadcasts) };
    self.subscriptions.push(hub.subscribe( filter, fun ));
  };

  // Receiver path can be falsey for broadcast messages.
  self.approvalHubSend = function( approval, senderPath, receiverPath ) {
    hub.send( "approval-status",
              { broadcast: _.isEmpty(senderPath),
                approval: _.clone(approval),
                docId: self.docId,
                path: senderPath,
                receiver: receiverPath});
  };

  self.approvalModel = new LUPAPISTE.DocumentApprovalModel(self);
  self.isApproved = self.approvalModel.isApproved;

  self.isPostVerdictEdited = function () {
    return _.get( self.meta, "_post_verdict_edit.timestamp") > 0;
  };

  self.isPostVerdictSent = function () {
    return _.get( self.meta, "_post_verdict_sent.timestamp") > 0;
  };

  self.noteText = function(noteData, type) {
    var text = null;
    if(noteData && noteData.user && noteData.timestamp) {
      text = sprintf("%s %s: %s %s",
        loc(["document", type]),
        moment(noteData.timestamp).format("D.M.YYYY HH:mm"),
        noteData.user.firstName,
        noteData.user.lastName);
    }
    return text;
  };

  self.editNote = function () {
    return self.noteText(_.get( self.meta, "_post_verdict_edit"), "edited");
  };

  self.sentNote = function () {
    return self.noteText(_.get( self.meta, "_post_verdict_sent"), "sent");
  };

  //----------------------------------------------------------------------

  // Returns id if we are in the testing mode, otherwise null.
  // Null because Knockout does not render null attributes.
  self.testId = function( id ) {
    return options && options.dataTestSpecifiers ? id : null;
  };

  self.approvalTestId = function( path, verb ) {
    return self.testId( [verb, "doc", _.head( path) || self.schemaName].join ( "-" ));
  };

  self.removeDocument = function() {
    var op = self.schema.info.op;

    var documentName = "";
    if (op) {
      documentName = loc([op.name, "_group_label"]);
    } else {
      documentName = loc([self.schema.info.name, "_group_label"]);
    }

    function onDocumentRemoved() {
      // This causes full re-rendering, all accordions change
      // state etc. Figure a better way to update UI.  Just the
      // "operations" list should be changed.
      repository.load(self.appId);
    }

    function onRemovalConfirmed() {
      ajax.command("remove-doc", { id: self.appId, docId: self.docId, collection: self.getCollection() })
        .success(onDocumentRemoved)
        .onError("error.document-not-found", onDocumentRemoved)
        .onError("error.removal-of-last-document-denied", notify.ajaxError)
        .call();
    }

    var message = "<div>"
                + loc("removeDoc.message1")
                + " <strong>" + documentName + ".</strong></div><div>"
                + loc("removeDoc.message2") + "</div>";
    hub.send("show-dialog", {ltitle: "removeDoc.sure",
                             size: "medium",
                             component: "yes-no-dialog",
                             componentParams: {text: message,
                                               yesFn: onRemovalConfirmed,
                                               lyesTitle: "removeDoc.ok",
                                               lnoTitle: "removeDoc.cancel"} });
  };

  function getUpdateCommand() {
    if (self.docPostVerdictEdit && self.authorizationModel.ok("update-post-verdict-doc")) {
      return "update-post-verdict-doc";
    }
    return _.get( options, "updateCommand", "update-doc" );
  }

  // Initializes element validation details (class, attributes,
  // property) according to the initial, "load-time" validation
  // results. When a component is edited, its validation status is
  // updated via showValidationResults function.
  function initialValidation( path, element, validationResult ) {
    var level = _.first( validationResult );
    if( level ) {
      var elem$ = $(element).addClass( level );
      if( level !== "tip" ) {
        elem$.attr( "data-docgen-validation", true )
          .attr( "aria-invalid", true )
          .attr( "aria-errormessage", docutils.warningId( {document: {id: self.docId},
                                                           path: path}));
      } else {
        elem$.attr( "aria-required", true ).prop( "required", true );
      }
    }
  }

  // Element constructors
  // Supported options:
  // icon: icon class -> <button><i class="icon"></i><span>label</span></button>
  // className: class attribute value -> <button class="className">...
  // testId: data-test-id attribute value.
  function makeButton(id, label, opts) {
    opts = opts || {};
    var button = document.createElement("button");
    button.id = id;
    if( opts.icon ) {
      var i = document.createElement( "i");
      i.setAttribute( "class", opts.icon );
      i.setAttribute( "aria-hidden", true );
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
    if( opts.testId ) {
      button.setAttribute( "data-test-id", opts.testId );
    }
    return button;
  }

  function makeLabel(schema, type, pathStr, validationResult) {
    var label = document.createElement("label");
    var path = pathStr;
    switch(type) {
      case "table":
      case "group":
      case "select":
        path = pathStr + "._group_label";
        break;
    }

    var locKey = util.locKeyFromDocPath(self.schemaI18name + "." + path);
    if (schema.i18nkey) {
      locKey = schema.i18nkey;
    }

    var className = "lux form-label-" + type;
    if (schema.labelclass) {
      className = className + " " + schema.labelclass;
    }
    if (validationResult && validationResult[0]) {
      var level = validationResult[0];
      className += " " + level;
    }

    label.id = pathStrToLabelID(pathStr);
    label.htmlFor = pathStrToID(pathStr);
    label.className = className;
    label.innerHTML = loc(locKey);
    return label;
  }

  function sourceValueChanged(input, value, sourceValue, source,
                              localizedSourceValue, titleTarget ) {
    titleTarget =  titleTarget || input;
    if (sourceValue === value || !source) {
      titleTarget.removeAttribute("title");
      $(input).removeClass("source-value-changed");
    } else if (sourceValue !== undefined && sourceValue !== value){
      $(input).addClass("source-value-changed");
      if (!_.endsWith(input.name, "hetu")) {
        titleTarget.title = _.escapeHTML(loc("sourceValue") + ": " + (localizedSourceValue ? localizedSourceValue : sourceValue));
      }
    }
  }


  function setDisplayWhen(input, path, subSchema, setAtt, useFullPath) {
    var displayWhenValues = [];
    var displayWhen = subSchema["show-when"] || subSchema["hide-when"];

    if( !flagsService.includeSchema( displayWhen )) {
      // Nothing to do here
      return;
    }

    var mode = subSchema["show-when"] ? "show" : "hide";

    displayWhen.values.forEach(function (item, index) {
      var itemToAdd = item.toString().concat("||");
      if (index === displayWhen.values.length - 1) {
        displayWhenValues.push(item);
      } else {
        displayWhenValues.push(itemToAdd);
      }
    });

    // The basic rule is that the related component, which value defines whether a component
    // should be hidden or shown, is on the same level as the target component that
    // should be shown conditionally (i.e. inside the same group, or on root level of a document
    // scheme's body).

    // There are 3 uses cases when computing the path of the related component:

    // 1) When target compomnent is a table row or a repeating element append button,
    // full path should be used. In this case repeating elements path does not contain name
    // of the repeating element.

    // 2) When target compomnent is a repeating group (but not table), path contains index in
    // addition to the name. In this case, the last path segment is integer. If group is
    // added by using append button, the path segement is JS number. If and group that is added
    // earlier, lasta segment is string that contains only digits.

    // NOTE: as consequence, you cannot (and you should not) use only numbers in the name of an
    // elements that is supposed to be hidden conditionally.

    // 3) Otherwise the last segement in the path is the element itself, and you can form sibling
    // node by replacing the last node (node name) with show-when (or hide-when) component path.

    var onlyDigits = /^\d+$/;
    var lastPathSegement = path[path.length-1];
    var isRepeatingGroupNode =
        typeof (lastPathSegement) === "number" ||
        (typeof (lastPathSegement) === "string" &&
          onlyDigits.test(path[path.length-1]));

    var pathToValue =
        useFullPath ?
          /* case 1 */
          _.clone(path) :
          /* case 2 (drop last 2) and case 3 (drop last 1) */
          path.slice(0, isRepeatingGroupNode ? -2 : -1);

    pathToValue.push(displayWhen.path);

    if (setAtt) {
      input.setAttribute("path-for-display-when", pathStrToID(pathToValue.join(".")));
      input.setAttribute("values-for-display-when", displayWhenValues);
      input.setAttribute("mode-for-display-when", mode );
    } else {
      input.attr("path-for-display-when", pathStrToID(pathToValue.join(".")));
      input.attr("values-for-display-when", displayWhenValues);
      input.attr("mode-for-display-when", mode );
    }
  }

  function makeInput(type, path, modelOrValue, subSchema, validationResult, checkboxLabel) {
    var pathStr = path.join(".");
    var value = _.isObject(modelOrValue) ? getModelValue(modelOrValue, subSchema.name) : modelOrValue;
    var sourceValue = _.isObject(modelOrValue) ? getModelSourceValue(modelOrValue, subSchema.name) : undefined;
    var source = _.isObject(modelOrValue) ? getModelSource(modelOrValue, subSchema.name) : undefined;
    var extraClass = self.sizeClasses[subSchema.size] || "";

    var readOnly = isInputReadOnly(doc, subSchema, modelOrValue);
    var isSpan = readOnly && type !== "checkbox";
    var input = document.createElement( isSpan ? "span" : "input");
    input.id = pathStrToID(pathStr);
    input.name = self.docId + "." + pathStr;
    input.setAttribute("data-docgen-path", pathStr);

    setDisplayWhen( input, path, subSchema, true);

    input.className = "form-input lux " + type + " " + (extraClass || "");

    initialValidation( path, input, validationResult );

    if( isSpan ) {
      input.textContent = value;
      return input;
    }

    // The remaining function body applies only to input elements.
    try {
      input.type = type;
    } catch (e) {
      // IE does not support HTML5 input types such as email
      input.type = "text";
    }

    if ( readOnly ) {
      input.readOnly = true;
    } else {
      input.onchange = function(e) {
        if (type === "checkbox") {
          sourceValueChanged(input, input.checked, sourceValue, source,
                             sourceValue ? loc("selected") : loc("notSelected"),
                            checkboxLabel );
        } else {
          sourceValueChanged(input, input.value, sourceValue, source);
        }
        save(e, function() {
          if (subSchema) {
            emit(getEvent(e).target, {subSchema: subSchema});
          }
        });
      };
    }

    if (type === "checkbox") {
      input.checked = value;
      sourceValueChanged(input, value, sourceValue, source,
                         sourceValue ? loc("selected") : loc("notSelected"),
                        checkboxLabel);
    } else {
      input.value = value || "";
      sourceValueChanged(input, value, sourceValue, source);

      if (subSchema.placeholder && _.includes(["text", "email", "search", "url", "tel", "password"], type) && !self.isDisabled) {
        input.setAttribute("placeholder", loc(subSchema.placeholder));
      }
    }
    return input;
  }

  function makeErrorPanel(pathStr, validationResult) {
    var errorPanel = document.createElement("span");
    errorPanel.setAttribute( "data-docgen-error", false );
    errorPanel.className = "errorPanel";
    errorPanel.id = pathStrToID(pathStr) + "-error";

    if (validationResult && validationResult[0] !== "tip") {
      var level = validationResult[0];
      var code = validationResult[1];
      var errorSpan = document.createElement("span");
      errorSpan.className = level;
      errorSpan.innerHTML = loc(["error", code]);
      errorPanel.appendChild(errorSpan);
    }
    return errorPanel;
  }

  function makeEntrySpan(subSchema ) {
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

    // Allow special styles for "other, what?" style fields
    if (subSchema["show-when"] || subSchema["hide-when"]) {
      span.className += " toggle-when";
    }

    return span;
  }


  // Form field builders

  function makeWrapper( input, label, classPrefix ) {
    var wrapper = document.createElement( "div" );
    classPrefix = classPrefix || "blockbox";
    wrapper.className = classPrefix + "-wrapper";
    wrapper.append( input );

    if ( label) {
      label.className = classPrefix + "-label";
      input.setAttribute( "aria-label", label.innerHTML );
      label.setAttribute( "aria-hidden", true ) ;
      wrapper.append( label );
    }
    return wrapper;
  }

  function buildCheckbox(subSchema, model, path) {
    var myPath = path.join(".");
    var validationResult = getValidationResult(model, subSchema.name);
    var span = makeEntrySpan(subSchema, myPath, validationResult);
    if (!_.isEmpty(subSchema.css)) {
      span.className += " " + _.join(subSchema.css, " ");
    }
    var label = subSchema.label
        ? makeLabel(subSchema, "checkbox", myPath, validationResult)
        : null;
    var input = makeInput("checkbox", path, model, subSchema, validationResult,
                         label);

    input.disabled = isInputReadOnly(doc, subSchema, model);

    var wrapper =  makeWrapper( input,
                                label,
                                subSchema.classPrefix || "blockbox");

    span.append( wrapper );

    listen(subSchema, myPath, input);

    return span;
  }

  function buildString(subSchema, model, path) {
    var myPath = path.join(".");
    var validationResult = getValidationResult(model, subSchema.name);
    var span = makeEntrySpan(subSchema, myPath, validationResult);

    var supportedInputSubtypes = ["email", "time"];
    var inputType = _.includes(supportedInputSubtypes, subSchema.subtype) ? subSchema.subtype : "text";

    var input = makeInput(inputType, path, model, subSchema, validationResult);
    docutils.setMaxLen(input, subSchema);

    listen(subSchema, myPath, input);

    if (subSchema.label) {
      span.appendChild(makeLabel(subSchema, "string", myPath, validationResult));
    }

    if (subSchema.subtype === "maaraala-tunnus" ) {
      var kiitunAndInput = document.createElement("span");
      var kiintun = document.createElement("span");

      kiitunAndInput.className = "kiintun-and-maaraalatunnus";

      kiintun.className = "form-maaraala";
      kiintun.appendChild(document.createTextNode(util.prop.toHumanFormat(self.propertyId) + "-M"));

      docutils.bindHelp( kiintun, span);

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

      docutils.bindHelp( input, span );

      inputAndUnit.appendChild(unit);
      span.appendChild(inputAndUnit);

    } else {
      docutils.bindHelp( input, span );
      span.appendChild(input);
    }

    return span;
  }

  function getValidationResult(model, name) {
    return util.getIn(model, [name, "validationResult"]);
  }

  function getModelValue(model, name) {
    return util.getIn(model, [name, "value"], "");
  }

  function getModelSourceValue(model, name) {
    return util.getIn(model, [name, "sourceValue"]);
  }

  function getModelSource(model, name) {
    return util.getIn(model, [name, "source"]);
  }

  function isInputReadOnly(doc, subSchema, model) {
    var modelDisabled = util.getIn(model, [subSchema.name, "whitelist-action"]) === "disabled";
    var readonlyByState = subSchema["readonly-after-sent"] && "sent" === doc.state;
    return subSchema.readonly || readonlyByState || modelDisabled;
  }

  function matchesWhitelist(schema, attr, item) {
    var whitelist = util.getIn(schema, ["whitelist", attr]);
    return _.isEmpty(whitelist) || _.includes(whitelist, item);
  }

  // Returns true if the subschema passes any whitelist requirements it has
  // whitelistType is either "disabled" or "hidden"
  function isSubSchemaWhitelisted(schema, whitelistType) {
    // Given whitelist doesn't apply to this schema; automatically whitelisted
    if (util.getIn(schema, ["whitelist", "otherwise"]) !== whitelistType) {
      return true;
    }
    var rolesOk = matchesWhitelist(schema, "roles", lupapisteApp.models.currentUser.applicationRole());
    var permitTypeOk = matchesWhitelist(schema, "permitType", lupapisteApp.models.application.permitType());
    return rolesOk && permitTypeOk;
  }

  function buildText(subSchema, model, path) {
    var myPath = path.join(".");
    var validationResult = getValidationResult(model, subSchema.name);
    var input = document.createElement("textarea");
    var span = makeEntrySpan(subSchema, myPath, validationResult);

    input.id = pathStrToID(myPath);

    docutils.bindHelp( input, span );

    input.name = myPath;
    input.setAttribute("rows", subSchema.rows || "10");
    input.setAttribute("cols", subSchema.cols || "40");
    docutils.setMaxLen(input, subSchema);

    input.className = "form-input lux textarea";
    if (validationResult && validationResult[0]) {
      var level = validationResult[0];
      input.className += " " + level;
    }

    var value = getModelValue(model, subSchema.name);
    input.value = value;

    if (subSchema.placeholder && !self.isDisabled) {
      input.setAttribute("placeholder", loc(subSchema.placeholder));
    }
    setDisplayWhen(input, path, subSchema, true);

    var sourceValue = _.isObject(model) ? getModelSourceValue(model, subSchema.name) : undefined;
    var source = _.isObject(model) ? getModelSource(model, subSchema.name) : undefined;

    sourceValueChanged(input, value, sourceValue, source);

    if (isInputReadOnly(doc, subSchema, model)) {
      input.readOnly = true;
    } else {
      input.onchange = function(e) {
        sourceValueChanged(input, input.value, sourceValue, source);
        save(e);
      };
    }

    if (subSchema.label) {
      span.appendChild(makeLabel(subSchema, "text", myPath, validationResult));
    }
    span.appendChild(input);
    return span;
  }

  function buildDate(subSchema, model, path, transform) {
    transform = _.merge( {toDate: _.identity, fromDate: _.identity },
                         transform );
    var lang = loc.getCurrentLanguage();
    var myPath = path.join(".");
    var validationResult = getValidationResult(model, subSchema.name);
    var value = transform.toDate(getModelValue(model, subSchema.name));

    var span = makeEntrySpan(subSchema, myPath, validationResult);

    if (subSchema.label) {
      span.appendChild(makeLabel(subSchema, "date", myPath, validationResult));
    }

    var className = "form-input lux form-date";
    if (validationResult && validationResult[0]) {
      var level = validationResult[0];
      className += " " + level;
    }
    // date
    var input = $("<input>", {
      id: pathStrToID(myPath),
      "data-docgen-path": myPath,
      name: self.docId + "." + myPath,
      type: "text",
      "class": className,
      value: value
    });

    setDisplayWhen( input, path, subSchema, false );

    var sourceValue = getModelSourceValue(model, subSchema.name);
    var source = getModelSource(model, subSchema.name);

    sourceValueChanged(input.get(0), value, sourceValue, source);

    if (isInputReadOnly(doc, subSchema, model)) {
      input.attr("readonly", true);
    } else {
      input.datepicker($.datepicker.regional[lang]).on("change",function(e) {
        var date = util.finnishDate(input.val(),
                                    loc.getCurrentLanguage());
        // The date field is always shown in the Finnish format.
        input.val( date );
        sourceValueChanged(input.get(0), date, sourceValue, source);
        var value = transform.fromDate( date );
        saveValue(e, value);
        emit( e.target, {subSchema: subSchema,
                         value: value });
      });
    }
    docutils.bindHelp( input, span );
    input.appendTo(span);

    return span;
  }

  function parseDateStringToMs(dateString) {
    // The date has been converted to the Finnish format before
    // transform.
    var m = util.toMoment( dateString, "fi");
    return m && m.valueOf();
  }

  function buildMsDate(subSchema, model, path) {
    return buildDate( subSchema, model, path,
                    {fromDate: parseDateStringToMs,
                     toDate: util.finnishDate});
  }

  function buildTime(subSchema, model, path) {
    // Set implisit options into a clone
    var timeSchema = _.clone(subSchema);
    timeSchema.subtype = "time";
    timeSchema.size = "m";
    timeSchema["max-len"] = 10; // hh:mm:ss.f
    return buildString(timeSchema, model, path);
  }

  function buildSelect(subSchema, model, path) {
    var myPath = path.join(".");
    var select = document.createElement("select");
    var validationResult = getValidationResult(model, subSchema.name);
    var selectedOption = getModelValue(model, subSchema.name);
    var sourceValue = getModelSourceValue(model, subSchema.name);
    var source = getModelSource(model, subSchema.name);
    var span = makeEntrySpan(subSchema, myPath, validationResult);
    var sizeClass = self.sizeClasses[subSchema.size] || "";

    docutils.bindHelp( select, span );
    select.setAttribute("data-docgen-path", myPath);
    select.setAttribute("data-test-id", myPath);

    select.name = myPath;
    select.className = "form-input lux " + (sizeClass || "");

    initialValidation( path, select, validationResult );

    select.id = pathStrToID(myPath);

    var otherKey = subSchema["other-key"];
    if (otherKey) {
      var pathToOther = path.slice(0, -1);
      pathToOther.push(otherKey);
      select.setAttribute("data-input-other-id", pathStrToID(pathToOther.join(".")));
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

    function selectEmit( target, sendLater ) {
      var opts = target.value === "other" ?
                   {locName: loc("select-other")} :  _.find( options, {name: target.value});
      emit( target, _.defaults( {sendLater: Boolean( sendLater ),
                                 subSchema: subSchema},
                                opts));
    }

    var locSelectedOption = _.find(options, function(e) {
      return e.name === sourceValue;
    });

    sourceValueChanged(select, selectedOption, sourceValue, source, locSelectedOption ? locSelectedOption[1] : undefined);

    if (isInputReadOnly(doc, subSchema, model)) {
      select.disabled = true;
    } else {
      select.onchange = function(e) {
        sourceValueChanged(select, select.value, sourceValue, source, locSelectedOption ? locSelectedOption[1] : undefined);
        save(e);
        selectEmit( e.target );
      };
    }

    listen(subSchema, myPath, select);

    selectEmit(select, true);

    if (subSchema.label) {
      span.appendChild(makeLabel(subSchema, "select", myPath, validationResult));
    }
    span.appendChild(select);
    span.appendChild(makeErrorPanel(myPath, validationResult));
    setDisplayWhen(select, path, subSchema, true);

    return span;
  }

  // Returns object with fun and attr properties or null, if the given
  // subSchema/path does not support/require remove functionality.
  function resolveRemoveOptions( subSchema, path ) {
    var opts = null;
    if(subSchema.repeating && !self.isDisabled && authorizationModel.ok("remove-document-data")) {
      opts = {
        fun: function () {
          hub.send("show-dialog", {ltitle: "document.delete.header",
                                   size: "medium",
                                   component: "yes-no-dialog",
                                   componentParams: {ltext: "document.delete.message",
                                                     yesFn: function () { removeData(self.appId, self.docId, path); } }});
        }
      };
      if( options && options.dataTestSpecifiers ) {
        opts.attr =  {"data-test-class": "delete-schemas." + subSchema.name};
      }
    }
    return opts;
  }

  function buildGroup(subSchema, model, path) {
    var myPath = path.join(".");
    var name = subSchema.name;
    var myModel = model[name] || {};
    var partsDiv = document.createElement("div");
    var div = document.createElement("div");

    appendElements(partsDiv, subSchema, myModel, path, save);

    div.id = pathStrToGroupID(myPath);
    div.className = subSchema.layout === "vertical" ? "form-choice" : "form-group";
    if (!_.isEmpty(subSchema.css)) {
      div.className += " " + _.join(subSchema.css, " ");
    }

    var removeOptions = resolveRemoveOptions(subSchema, path);
    if (subSchema.approvable || removeOptions) {
      $(div).append(createComponent("group-approval",
                                    {docModel: self,
                                     subSchema: subSchema,
                                     model: myModel,
                                     path: path,
                                     remove: removeOptions}));
    }

    $(div).append( createComponent( "docgen-group-info",
                                    {document: self,
                                     model: model,
                                     schema: subSchema,
                                     path: path}));

    var groupHelpText = docutils.makeGroupHelpTextSpan(subSchema);
    div.appendChild(groupHelpText);

    div.appendChild(partsDiv);

    setDisplayWhen( partsDiv, path, subSchema, true );

    listen(subSchema, myPath, div);
    return div;
  }

  // List of document ids added into document data service after docmodel loaded.
  // Multiple additions is restricted but document is reloaded after repository load.
  var updatedDocumentsInDocumentDataService = [];

  function buildGroupComponent (name, subSchema, model, path) {
    var i18npath = subSchema.i18nkey ? [subSchema.i18nkey] : [self.schemaI18name].concat(_.reject(path, _.isNumber));

    if (!_.includes(updatedDocumentsInDocumentDataService, doc.id)) {
      lupapisteApp.services.documentDataService.addDocument(doc, {isDisabled: self.isDisabled});
      updatedDocumentsInDocumentDataService.push(doc.id);
    }

    var params = {
      applicationId: self.appId,
      documentId: self.docId,
      schemaI18name: self.schemaI18name,
      path: path,
      i18npath: i18npath,
      schema: subSchema,
      model: model[subSchema.name],
      location: self.application["location-wgs84"],
      isDisabled: self.isDisabled,
      authModel: self.authorizationModel,
      propertyId: self.propertyId,
      propertyIdSource: self.propertyIdSource,
      docModel: self,
      validationErrors: doc.validationErrors
    };

    return createComponent(name, params);
  }

  function buildDocgenGroup (subSchema, model, path) {
    var name = subSchema.repeating ? "docgen-repeating-group" : "docgen-group";
    return buildGroupComponent(name, subSchema, model, path);
  }

  function buildLinkPermitSelector (subSchema, model, path) {
    return buildGroupComponent("link-permit-selector", subSchema, model, path);
  }

  function buildPropertyGroup (subSchema, model, path) {
    return buildGroupComponent("property-group", subSchema, model, path);
  }

  function buildDocgenTable (subSchema, model, path) {
    return buildGroupComponent("docgen-table", subSchema, model, path);
  }

  function buildDocgenHuoneistotTable (subSchema, model, path) {
    return buildGroupComponent("docgen-huoneistot-table", subSchema, model, path);
  }

  function buildConstructionWasteReport (subSchema, model, path) {
    return buildGroupComponent("construction-waste-report", subSchema, model, path);
  }

  function buildDocgenBuildingSelect( subSchema, model, path ) {
    return buildGroupComponent( "docgen-building-select", subSchema, model, path );
  }

  function buildDocgenPersonSelect( subSchema, model, path ) {
    return buildGroupComponent( "docgen-person-select", subSchema, model, path );
  }

  function buildFundingSelect( subSchema, model, path) {
    return buildGroupComponent( "docgen-funding-select", subSchema, model, path);
  }

  function buildDocgenPropertyList( subSchema, model, path) {
    return buildGroupComponent( "docgen-property-list", subSchema, model, path);
  }

  function buildRadioGroup(subSchema, model, path) {
    var myPath = path.join(".");
    var validationResult = getValidationResult(model, subSchema.name);
    var myModel;
    if (model[subSchema.name] && model[subSchema.name].value) {
      myModel = model[subSchema.name].value;
    } else {
      myModel = _.head(subSchema.body).name;
    }

    var partsDiv = document.createElement("div");
    partsDiv.type = "radioGroup";

    var span = makeEntrySpan(subSchema, myPath, validationResult);
    span.className = span.className + " radioGroup";
    span.setAttribute( "role", "radiogroup" );
    var ariaLabel = _.get( subSchema, "aria-label" );
    if( ariaLabel ) {
      span.setAttribute( "aria-label", loc( ariaLabel ));
    }
    partsDiv.id = pathStrToID(myPath);
    partsDiv.className = subSchema.name + "-radioGroup";

    $.each(subSchema.body, function (i, o) {
      var pathForId = myPath + "." + o.name;
      var input = makeInput("radio", path, o.name, subSchema);
      input.id = pathStrToID(pathForId);
      input.checked = o.name === myModel;
      var label = subSchema.label && makeLabel(subSchema, "radio", pathForId);
      var wrapper = makeWrapper( input, label, o.classPrefix || "radio");
      if( label && o.icon ) {
        var text = document.createElement( "span" );
        text.innerHTML = label.innerHTML;
        text.className = "gap--l1";
        var icon = document.createElement( "i" );
        icon.className = o.icon;
        label.innerHTML = "";
        label.appendChild( icon );
        label.appendChild( text );
      }
      span.appendChild( wrapper );
    });

    partsDiv.appendChild(span);
    return partsDiv;
  }

  function paramsStr(params) {
    return _.map(_.keys(params), function(key) {
      return key + ": " + key;
    }).join(", ");
  }

  function createComponent(name, params, classes) {
    // createElement works with IE8
    var element = document.createElement(name);
    ko.options.deferUpdates = true; // http://knockoutjs.com/documentation/deferred-updates.html

    var finalParams = _.defaults(params, {documentAuthModel: self.authorizationModel});

    $(element)
      .attr("params", paramsStr(finalParams))
      .addClass(classes ? classes + " docgen-component" : "docgen-component")
      .applyBindings(params);

    ko.options.deferUpdates = false;

    $(element).on("remove", function(event) {
      ko.cleanNode(event.target);
    });
    return element;
  }

  function buildForemanHistory() {
    var params = {
      applicationId: self.appId
    };
    return createComponent("foreman-history", params, "form-table");
  }

  function buildForemanOtherApplications(subSchema, model, path) {
    if (!_.includes(updatedDocumentsInDocumentDataService, doc.id)) {
      lupapisteApp.services.documentDataService.addDocument(doc, {isDisabled: self.isDisabled});
      updatedDocumentsInDocumentDataService.push(doc.id);
    }

    var i18npath = subSchema.i18nkey ? [subSchema.i18nkey] : [self.schemaI18name].concat(_.reject(path, _.isNumber));
    var params = {
      applicationId: self.appId,
      authModel: self.authorizationModel,
      documentId: self.docId,
      documentName: self.schemaName,
      hetu: undefined,
      model: model[subSchema.name] || {},
      schema: subSchema,
      path: path,
      i18npath: i18npath,
      schemaI18name: self.schemaI18name,
      validationErrors: doc.validationErrors
    };

    return createComponent("foreman-other-applications", params, "form-table");
  }

  function buildFillMyInfoButton(subSchema, model, path) {
    if (self.isDisabled || (model.fillMyInfo && model.fillMyInfo.disabled)) {
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

  function buildCompanySelector(subSchema, model, path) {

    function mapCompany(company) {
      company.displayName = ko.pureComputed(function() {
        return ko.unwrap(company.name) + " (" + ko.unwrap(company.y) + ")";
      });
      return company;
    }

    if (!self.isDisabled) {
      var myNs = path.slice(0, path.length - 1).join(".");

      var companies = _(lupapisteApp.models.application.roles() || [])
                      .filter(function(r) {
                        return ko.unwrap(r.type) === "company";
                      })
                      .map(mapCompany)
                      .value();

      var params = {
        id: self.appId,
        companies: companies,
        authModel: self.authorizationModel,
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
  }

  function buildTableRow(subSchema, model, path) {
    var myPath = path.join(".");
    var name = subSchema.name;
    var myModel = model[name] || {};
    var row = document.createElement("tr");
    appendElements(row, subSchema, myModel, path, save);

    row.id = pathStrToGroupID(myPath);
    var rm = resolveRemoveOptions( subSchema, path );
    if( rm ) {
      var icon$ = $("<i>").addClass( "lupicon-remove primary is-middle");
      icon$.on("click", rm.fun );
      _.each( rm.attr || {}, function( v, k ) {
        icon$.attr( k, v );
      });
      $(row).append( $("<td>").append( icon$ ));
    }
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
    docgenGroup: buildDocgenGroup,
    docgenTable: buildDocgenTable,
    propertyGroup: buildPropertyGroup,
    linkPermitSelector: buildLinkPermitSelector,
    docgenHuoneistot: buildDocgenHuoneistotTable,
    constructionWasteReport: buildConstructionWasteReport,
    string: buildString,
    hetu: buildString,
    text: buildText,
    checkbox: buildCheckbox,
    select: buildSelect,
    radioGroup: buildRadioGroup,
    date: buildDate,
    time: buildTime,
    msDate: buildMsDate,
    // element: buildElement,
    buildingSelector: buildDocgenBuildingSelect,
    fillMyInfoButton: buildFillMyInfoButton,
    foremanHistory: buildForemanHistory,
    "hanke-table": buildForemanOtherApplications,
    personSelector: buildDocgenPersonSelect,
    companySelector: buildCompanySelector,
    table: buildTableRow,
    fundingSelector: buildFundingSelect,
    unknown: buildUnknown,
    "docgen-property-list": buildDocgenPropertyList
  };

  function removeData(id, doc, path) {
    ajax
      .command("remove-document-data", { doc: doc, id: id, path: path, collection: self.getCollection() })
      .success(function () {
        repository.load(id);
      })
      .call();
  }

  function build(subSchema, model, path) {

    // Do not create hidden whitelisted elements
    var schemaBranchHidden = !isSubSchemaWhitelisted(subSchema, "hidden");
    var schemaLeafHidden = util.getIn(model, [subSchema.name, "whitelist"]) === "hidden";
    var schemaIncluded = flagsService.includeSchema(subSchema);

    if (subSchema.hidden || schemaLeafHidden || schemaBranchHidden || !schemaIncluded) {
      return;
    }

    if (subSchema.label === undefined) {
      subSchema.label = true;
    }

    var myName = subSchema.name;
    var myPath = path.concat([myName]);
    var builder = builders[subSchema.uicomponent] || builders[subSchema.type] || buildUnknown;
    var repeatingId = myPath.join("-");

    function makeElem(myModel, id) {
      var elem = builder(subSchema, myModel, myPath.concat([id]));
      if (elem) {
        elem.setAttribute("data-repeating-id", repeatingId);
        elem.setAttribute("data-repeating-id-" + repeatingId, id);
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
      var elements = buildElements(models);

      if (subSchema.type === "table") {
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

        if (subSchema.approvable) {
          $(div).append(createComponent( "group-approval",
                                         {docModel: self,
                                          subSchema: subSchema,
                                          model: models,
                                          path: myPath}));
        }

        var label = makeLabel(subSchema, "table", myPath.join("."));
        div.appendChild(label);

        var groupHelpText = docutils.makeGroupHelpTextSpan(subSchema);
        div.appendChild(groupHelpText);

        div.appendChild(table);
        setDisplayWhen(div, path, subSchema, true, true);
        elements = [div];
      }

      var buttonTestId = myPath.join("_") + "_append";
      var appendButton = makeButton(self.docId + "_" + buttonTestId,
                                    loc( util.locKeyFromDocPath(_.flatten([self.schemaI18name, myPath, "_append_label"]).join(".")) ),
                                    {icon: "lupicon-circle-plus",
                                     className: "secondary",
                                     testId: buttonTestId});
      appendButton.disabled = !isSubSchemaWhitelisted(subSchema, "disabled");

      var appender = function () {
        var index;
        if( lupapisteApp.services.documentDataService.findDocumentById( self.docId )) {
          index = lupapisteApp.services.documentDataService.addRepeatingGroup( self.docId, myPath );
        } else {
          var parent$ = $(this).closest(".accordion-fields");
          index = parent$.find("*[data-repeating-id='" + repeatingId + "']").length;
          while (parent$.find("*[data-repeating-id-" + repeatingId + "='" + index + "']").length) {
            index++;
          }
        }
        var myModel = {};
        myModel[myName] = {};
        var groupToAdd = makeElem(myModel, index);
        $(this).before(groupToAdd);
        docgen.initDisplayWhenFor("[path-for-display-when]", groupToAdd);
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

      var copyElement = function(event) {
        var clickedButton = event.currentTarget || event.target;
        var updates = {paths: [], values: []};
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
          var path = newInput$.attr("data-docgen-path");
          var oldInput$ = $(this).find("input, select");
          var prop = "value";
          if(oldInput$.is(":checkbox")) {
            prop = "checked";
          }
          var oldValue = oldInput$.prop(prop);
          if(oldValue) {
            newInput$.prop(prop, oldValue);
            updates.paths.push(path);
            updates.values.push(oldValue);
          }
        });
        saveMany(clickedButton, updates);
        parent$.append(newItem);
      };

      var buttonGroup = document.createElement("div");
      buttonGroup.className = "button-group";
      buttonGroup.appendChild(appendButton);
      setDisplayWhen(appendButton, path, subSchema, true, true);

      if (subSchema.type === "table") {
        $(appendButton).on("click",tableAppender);
        var locKey = [self.schemaI18name, myPath.join("."), "copyLabel"];
        if (subSchema.copybutton) {
          if (subSchema.i18nkey) {
            locKey = [subSchema.i18nkey, "copyLabel"];
          }
          var copyButton = makeButton(myPath.join("_") + "_copy", loc(locKey),
                                      {icon: "lupicon-circle-plus", className: "secondary"});
          $(copyButton).on("click",copyElement);
          buttonGroup.appendChild(copyButton);
        }
      } else {
        $(appendButton).on("click",appender);
      }

      elements.push(buttonGroup);
      return elements;
    }

    return builder(subSchema, model, myPath);
  }

  function getSelectOneOfDefinition(schema) {
    var selectOneOfSchema = _.find(schema.body, function (subSchema) {
      return subSchema.name === docutils.SELECT_ONE_OF_GROUP_KEY && subSchema.type === "radioGroup";
    });

    if (selectOneOfSchema) {
      return _.map(selectOneOfSchema.body, function (subSchema) { return subSchema.name; }) || [];
    }

    return [];
  }

  function appendElements(body, schema, model, path) {

    function toggleSelectedGroup(value) {
      $(body)
        .children("[data-select-one-of]")
        .hide()
        .filter("[data-select-one-of='" + value + "']")
        .show();
    }

    var selectOneOf = getSelectOneOfDefinition(schema);

    _.each(schema.body, function (subSchema) {
      var children = build(subSchema, model, path, save);
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
      var myModel = _.head(selectOneOf);
      if (model[docutils.SELECT_ONE_OF_GROUP_KEY]) {
        myModel = model[docutils.SELECT_ONE_OF_GROUP_KEY].value;
      }

      toggleSelectedGroup(myModel);

      var s = "[name$='." + docutils.SELECT_ONE_OF_GROUP_KEY + "']";
      $(body).find(s).on("change",function () {
        toggleSelectedGroup(this.value);
      });
    }

    return body;
  }

  function saveForReal(paths, values, callback) {
    var p = _.isArray(paths) ? paths : [paths];
    var v = _.isArray(values) ? values : [values];
    var updates = _.zip(
        _.map(p, function(path) {
          return path.replace(new RegExp("^" + self.docId + "."), "");
        }),
        v);
    var updateCommand = getUpdateCommand();

    function createHandler(logger) {
      return function (e) {
        logger(e);
        callback(updateCommand, "err", e.results);
      };
    }

    ajax
      .command(updateCommand, { doc: self.docId, id: self.appId, updates: updates, collection: self.getCollection() })
      // Server returns empty array (all ok), or array containing an array with three
      // elements: [key status message]. Here we use just the status.
      .success(function (e) {
        var status = (e.results.length === 0) ? "ok" : e.results[0].result[0];
        callback(updateCommand, status, e.results);
      })
      .onError("error.document-not-found", createHandler(debug))
      .onError("document-would-be-in-error-after-update", createHandler(debug))
      .error(createHandler(error))
      .fail(createHandler(error))
      .call();
  }

  self.showValidationResults = function(results) {
    // remove warning and error highlights
    var docIdSelector = "#document-" + self.docId;
    //$( docIdSelector + " [data-docgen-validation=true]" )
    $( docIdSelector + " [data-docgen-validation]")
      .removeClass( "warn")
      .removeClass( "error")
      .removeClass( "err")
      .removeClass( "tip" )
      .removeProp( "required" )
      .removeAttr( "aria-required" )
      .removeAttr( "aria-invalid" )
      .removeAttr( "data-docgen-validation")
      .removeAttr( "aria-errormessage");

    // // Reset error messages
    // $( docIdSelector + " [data-docgen-error=true]")
    //   .attr( "data-docgen-error", false )
    //   .remove( "span");

    // apply new errors & highlights
    if (results && results.length > 0) {
      _.each(results, function (r) {
        var level = r.result[0];
        var pathStr = r.path.join("-");
        var selector = "#" + pathStrToID( pathStr );
        var elem$ = $(selector);
        elem$.attr( "data-docgen-validation", true );
        if (level !== "tip") {
          elem$.attr( "aria-invalid", true )
            .attr( "aria-errormessage", "warn-" + pathStrToID( pathStr ));
        } else {
          elem$.attr( "aria-required", true ).prop( "required", true );
        }
        $("#" + pathStrToLabelID(pathStr)).addClass(level).attr( "data-docgen-validation", true );
        $(selector).addClass(level);
      });
    }
    hub.send( "docgen-validation-results", results );
  };

  function afterSave(paths, label, loader, target, callback, updateCommand, status, results) {
    self.showValidationResults(results);
    // if (label) {
    //   label.removeChild(loader);
    // }
    if (status === "warn" || status === "tip") {
      docutils.showPing( target, "positive" );
    } else if (status === "err") {
      docutils.showPing( target, "negative" );
    } else if (status === "ok") {
      docutils.showPing( target, "positive" );
    } else if (status !== "ok") {
      error("Unknown status:", status);
    }

    // Send updated event
    var eventType = updateCommand + "-success";
    hub.send(eventType, {appId: self.appId, documentId: self.docId, status: status, results: results,
                         paths: paths});


    if (callback) { callback(status, results); }
    // No return value or stopping the event propagation:
    // That would prevent moving to the next field with tab key in IE8.
  }

  function saveValue(e, value, callback) {
    var event = getEvent(e);
    var target = event.target;
    var path = target.name;
    //var loader = docutils.loaderImg();

    var label = document.getElementById(pathStrToLabelID(path));
    // if (label) {
    //   label.appendChild(loader);
    // }

    if (self.docPostVerdictEdit) {
      $(event.target).addClass("source-value-changed");
    }

    saveForReal(path, value, _.partial(afterSave, [path], label, null, target, callback));
  }

  function save(e, callback) {
    saveValue( e,
               e.target.type === "checkbox"
                 ? e.target.checked
                 : e.target.value,
               callback );
  }

  function saveMany(target, updates, callback) {
    var indicator = docutils.createIndicator(target);
        saveForReal(updates.paths, updates.values, _.partial(afterSave, updates.paths, null, null, indicator, callback));
  }

  // Typical options are value, path, subSchema and sendLater.
  // Other options are passed as well.
  var emitters = {
    filterByCode: function(event, opts) {
      var schemaValue = _.find(opts.subSchema.body, {name: opts.value});
      var code = schemaValue ? schemaValue.code : "";
      if (opts.sendLater) {
        self.events.push({name: event, data: {code: code}});
      } else {
        hub.send(event, {code: code});
      }
    },
    hetuChanged: function(event, opts) {
      hub.send(event, opts);
    },
    emitUnknown: function(event) {
      error("Unknown emitter event:", event);
    },
    accordionUpdate: function(event,  opts) {
      hub.send(event, _.defaults({docId: self.docId, applicationId: self.appId},
                                 opts));
    }
  };

  // Mandatory option: subSchema
  function emit(target, opts) {
    if (opts.subSchema.emit) {
      _.forEach(opts.subSchema.emit, function(event) {
        var emitter = emitters[event] || emitters.emitUnknown;
        emitter(event, _.defaults(_.clone( opts ),
                                  {value: target.value,
                                   path: $(target).attr("data-docgen-path")}));
      });
    }
  }

  function buildSection() {
    var section = $("<section>").addClass( "accordion").attr({"data-doc-type": self.schemaName, "data-doc-id": self.docId});

    var contents = $("<div>").addClass( "accordion_content");
    // id is used to remove validation indicators in showValidationResults
    contents.attr("id", "document-" + self.docId);
    function toggleContents( isOpen ) {
      contents.toggleClass( "expandend");
      contents.attr( "data-accordion-state",
                     isOpen ? "open" : "closed");
      if( LUPAPISTE.config.features.animations ) {
        var opts = {duration: 200,
                    easing: "easeInOutCubic"};
        var fun = isOpen ? "slideDown" : "slideUp";
        contents[fun]( opts );
      } else {
        contents.toggle( isOpen );
      }
    }
    contents.append(docutils.makeSectionHelpTextSpan(self.schema));
    var sticky =  $("<div>").addClass( "sticky");
    sticky.append(createComponent ( "accordion-toolbar",
                                    {docModel: self,
                                     docModelOptions: options,
                                     approvalModel: self.approvalModel,
                                     openCallback: toggleContents
                                    }));
    section.append( sticky );
    var elements = document.createElement("div");
    elements.className = "accordion-fields";
    appendElements(elements, self.schema, self.model, []);
    // Disable fields and hide if the form is not editable
    if (!self.authorizationModel.ok(getUpdateCommand()) || self.isDisabled) {
      $(elements).find("input, textarea").attr("readonly", true).off("focus");
        $(elements).find("select, input[type=checkbox], input[type=radio]")
            .not(".always-enabled")
            .attr("disabled", true);
      // TODO a better way would be to hide each individual button based on authorizationModel.ok
      $(elements).find("button").hide();
    }

    if (self.docPostVerdictEdit && self.authorizationModel.ok("update-post-verdict-doc")) {
      $(elements).find("button").hide();
      $(elements).find("div[data-repeating-id=rakennuksenOmistajat]").css({"pointer-events": "none"});
    }

    return section.append( contents.append( $(elements)));
  }

  self.dispose = function() {
    while (self.subscriptions.length > 0) {
      hub.unsubscribe(self.subscriptions.pop());
    }
    self.approvalModel.dispose();
    self.approvalModel = null;
  };

  self.element = buildSection();

  // Temporary storage for the document approval state after redraw.
  // The state is read and cleared by the reject-note component.
  self.redrawnDocumentApprovalState = ko.observable({});

  self.redraw = function() {
    // Refresh document data from backend
    ajax.query("document", {id: application.id, doc: doc.id, collection: self.getCollection()})
      .success(function(data) {
        var newDoc = data.document;
        self.model = newDoc.data;
        self.meta = newDoc.meta;
        self.docDisabled = newDoc.disabled;

        window.Stickyfill.remove($(".sticky", self.element));
        var accordionState = AccordionState.get(doc.id);
        _.set(options, "accordionCollapsed", !accordionState);

        var previous = self.element.prev();
        var next = self.element.next();
        var h = self.element.outerHeight();
        var placeholder = $("<div/>").css({"visibility": "hidden"}).outerHeight(h);
        self.element.before(placeholder);

        self.element.remove();
        self.element = buildSection();

        if (_.isEmpty(previous)) {
          next.before(self.element);
          _.delay(function() {
            placeholder.remove();
          },200);
        } else {
          previous.after(self.element);
          _.delay(function() {
            placeholder.remove();
          },200);
        }

        $(".sticky", self.element).Stickyfill();
        self.redrawnDocumentApprovalState( {approved: _.get( self.meta, "_approved.value")
                                            === "approved"});
      })
    .call();
  };

  self.approvalHubSubscribe(function() {
    authorization.refreshModelsForCategory(_.set({}, doc.id, authorizationModel), application.id, "documents");
  }, true);

  self.subscriptions.push(hub.subscribe({eventType: "category-auth-model-changed", targetId: doc.id}, function() {
    if (self.schema.info["redraw-on-approval"] && application.inPostVerdictState) {
      self.redraw();
    }
  }));

};
