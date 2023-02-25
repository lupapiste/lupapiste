// Accordion button and toolbar component.
// Parameters:
// docMdodel: DocModel instance
// docModelOptions: Options originally passed to DocModel
// openCallback: Callback that is called whan accordion opens/closes.
//               The callback takes care of showing/hding accordion
//               contents. It would be nicer, if the contents were
//               part of the component, but currently that is not
//               possible due to binding mismatches (contents contain
//               created components as well).
LUPAPISTE.AccordionToolbarModel = function( params ) {
  "use strict";
  var self = this;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  var APPROVE  = "approve";
  var REJECT   = "reject";

  self.docModel = params.docModel;
  self.docModelOptions = params.docModelOptions;
  self.accordionService = lupapisteApp.services.accordionService;
  self.assignmentService = lupapisteApp.services.assignmentService;
  self.approvalModel = params.approvalModel;
  self.auth = self.docModel.authorizationModel;
  self.isOpen = ko.observable();
  var isOpenSubs = self.isOpen.subscribe( params.openCallback );
  self.isOpen( !params.docModelOptions
            || !params.docModelOptions.accordionCollapsed);
  self.disabledStatus = ko.observable(!!self.docModel.docDisabled);

  AccordionState.register( self.docModel.docId, self.isOpen );

  self.info = self.docModel.schema.info;

  // Test ids must contain document name in order to avoid
  // hard to track selector conflicts in Robot tests.
  self.testId = function( id ) {
    return self.docModel.testId( id + "-" + self.docModel.schemaName);
  };

  // Operation data
  var op = self.info.op;
  // if service is defined use accordion service, if not (bulletins-app) use operation data from docgen
  self.operation = self.accordionService ? self.accordionService.getOperation(self.docModel.docId) : ko.mapping.fromJS(op);
  self.hasOperation = ko.pureComputed(function() {
    return _.isObject(op);
  });
  self.operationDescription = self.operation && self.operation.description || ko.observable();

  self.isPrimaryOperation = ko.pureComputed( function() {
    var id = op && op.id;
    return id && id === _.get( self.docModel, ["application",
                                               "primaryOperation",
                                               "id"] );
  });

  self.isSecretBuilding = ko.pureComputed(function() {
      var vtjprt = _.get(self, "docModel.model.valtakunnallinenNumero.value");
      var isSecret = false;
      if (vtjprt) {
          var documentBuildings = _.get(self, "docModel.application.document-buildings");
          isSecret = _.some(documentBuildings, {"vtj-prt": vtjprt, secret: true});
      }
      return isSecret;
  });

  self.showReplaceOperation = ko.pureComputed( function() {
    var isOp = op && op.id;
    return isOp && lupapisteApp.models.applicationAuthModel.ok("replace-operation");
  });

  self.replaceOperation = function() {
      pageutil.openPage("replace-operation", [self.docModel.appId, _.get(self.info, ["op", "id"])].join("/"));
      return false;
  };

  // identifier field is object with keys docId, schema, key, value. Value is observable (can be edited).
  self.identifierField = self.accordionService && self.accordionService.getIdentifier(self.docModel.docId);

  self.showIdentifierEditors = ko.observable(self.identifierField ? (self.identifierField.value() ? false : true) : false).extend({deferred:true});
  var stickyRefresh = self.showIdentifierEditors.subscribe(function() {
    // refresh accordion sitcky state
    _.delay(window.Stickyfill.rebuild, 0);
  });

  self.isArchiveProject = function () {
    return "ARK" === _.get(self.docModel, ["application", "permitType"]);
  };

  var docData = self.accordionService && self.accordionService.getDocumentData(self.docModel.docId);

  self.accordionText = ko.pureComputed(function() {
    // resolve values from given accordionPaths
    var paths = docData && docData.accordionPaths;
    return docutils.accordionText(paths, _.get(docData, "data"));
  });

  // Required accordion title from operation/schema-info name
  self.toolbarTitle = loc(((op && op.name) || self.info.name) + "._group_label");

  self.secretBuildingText = loc("rakennushanke.salainenRakennus");

  // Optional accordion header text.
  // Consists of optional properties: identifier field, operation description, and accordion paths (from schema)
  self.headerDescription = ko.pureComputed(function() {
    // if identifier exists, subscribing to its "value" observable
    var identifier = self.identifierField && self.identifierField.value();
    var operation  = self.operationDescription();
    var accordionText = self.accordionText();
    var disabledlocText = self.info.type === "party" ? "document.party.disabled" : "document.disabled";
    var disabledText = self.disabledStatus() ? " (" + loc(disabledlocText) + ")" : "";
    return docutils.headerDescription(identifier, operation, accordionText) + disabledText;
  });

  self.shouldShortenDescription = ko.pureComputed(function() {
    var maxHeaderLength = 160;
    var fullHeaderText = self.toolbarTitle + self.headerDescription() + self.secretBuildingText;
    return  self.isSecretBuilding() && fullHeaderText.length > maxHeaderLength;
  });

  self.toggleAccordion = function() {
    self.isOpen( !self.isOpen());
    setTimeout(function() { hub.send( "visible-elements-changed" ); }, 1000); // Visibility changes are not immediate
  };

  // Remove
  self.remove = {testClass: "delete-schemas."  + self.docModel.schemaName};

  self.showRemove = self.disposedComputed( function() {
    if (self.auth.ok("remove-doc") && !self.isPrimaryOperation()) {
      self.remove.fun = self.docModel.removeDocument;
      return true;
    }
  });

  var hasRole = util.getIn( lupapisteApp, ["models", "currentUser", "role"] );

  // Approval functionality
  self.isApprovable = Boolean(self.info.approvable);
  self.showStatus = self.approvalModel.showStatus;
  self.isApproved = hasRole && self.approvalModel.isApproved;
  self.isRejected = hasRole && self.approvalModel.isRejected;
  self.isSummaryRejected = hasRole && self.approvalModel.isSummaryRejected;
  self.details = self.approvalModel.details;
  self.editNote = ko.observable(self.docModel.editNote());
  self.sentNote = ko.observable(self.docModel.sentNote());

  self.approveTestId = self.docModel.approvalTestId( [], APPROVE );
  self.rejectTestId = self.docModel.approvalTestId( [], REJECT );

  function showButton( operation, excluder ) {
    return self.isApprovable
        && self.auth.ok( operation + "-doc")
        && (!excluder() || !self.showStatus());
  }

  self.showReject  = ko.pureComputed(_.partial ( showButton, REJECT, self.isRejected ));
  self.showApprove = ko.pureComputed(_.partial ( showButton, APPROVE, self.isApproved ));

  self.reject  = _.partial( self.approvalModel.changeStatus, false );
  self.approve = _.partial( self.approvalModel.changeStatus, true );

  self.showEditNote = ko.observable(self.docModel.isPostVerdictEdited());
  self.showSentNote = ko.observable(self.docModel.isPostVerdictSent());

  self.canBeDisabled = self.disposedPureComputed(function () {
    return self.auth.ok("set-doc-status");
  });
  self.changeDocStatus = function() {
    var currentValue = self.disabledStatus();
    var ajaxValue = !currentValue ? "disabled" : "enabled";
    var documentName = loc(self.titleLoc) + (self.accordionText() ? " - " + self.accordionText() : "");

    var setStatus = function() {
      ajax.command("set-doc-status", {id: self.docModel.appId, docId: self.docModel.docId, value: ajaxValue})
        .success(function(resp) {
          self.disabledStatus(!currentValue);
          util.showSavedIndicatorIcon(resp);
          // refreshing authorization makes docmodel be redrawn
          authorization.refreshModelsForCategory(_.set({}, self.docModel.docId, self.auth), self.docModel.appId, "documents");
          // Refresh assignments for document
          hub.send("assignmentService::targetsQuery", {applicationId: self.docModel.appId});
          hub.send("assignmentService::applicationAssignments", {applicationId: self.docModel.appId});
        })
        .call();
    };
    if (ajaxValue === "disabled") {
      hub.send("show-dialog", {ltitle: "areyousure",
                               size: "medium",
                               component: "yes-no-dialog",
                               componentParams: {text: loc("document.party.disabled.areyousure", documentName),
                                                 yesFn: setStatus,
                                                 lyesTitle: "document.party.disabled.areyousure.confirmation",
                                                 lnoTitle: "cancel"}});
    } else {
      setStatus();
    }
  };

  self.showToolbar = ko.pureComputed(function() {
    return hasRole &&  (self.showRemove() || self.showStatus()
                        || self.showReject() || self.showApprove()
                        || self.hasOperation() || self.canBeDisabled())
                        || self.showReplaceOperation;
  });

  self.closeEditors = function( data, event ) {
    // Toggle editors visibility with key press
    switch( event.keyCode ) {
      case 13: // Enter
      case 27: // Esc
      self.showIdentifierEditors(false);
      break;
    }
    return true;
  };

  var toggleEditorSubscription = hub.subscribe("accordionToolbar::toggleEditor", function(event) {
    if ((!event.docId || event.docId === self.docModel.docId) && self.hasOperation()) {
      var visibility = _.has(event, "show") ? Boolean(event.show) : !self.showIdentifierEditors();
      self.showIdentifierEditors(visibility);
    }
  });

  self.showToggleEdit = function () {
    return self.docModel.schema.info["post-verdict-editable"] &&
           self.docModel.authorizationModel.ok("update-post-verdict-doc");
  };

  self.editMode = function () {
    return self.docModel.docPostVerdictEdit;
  };

  self.toggleEdit = function () {
    self.docModel.docPostVerdictEdit = true;
    self.docModel.redraw();
  };

  self.sendEdit = function () {
    ajax.command("send-doc-updates", {id: self.docModel.appId, docId: self.docModel.docId})
      .success(function() {
        self.docModel.docPostVerdictEdit = false;
        self.showSentNote(self.docModel.isPostVerdictSent());
        self.sentNote(self.docModel.sentNote());
        self.docModel.redraw();
      })
      .error(function(e) {
        notify.ajaxError(e);
      })
      .call();
  };

  self.closeEdit = function () {
    self.docModel.docPostVerdictEdit = false;
    self.showEditNote(self.docModel.isPostVerdictEdited());
    self.editNote(self.docModel.editNote());
    self.docModel.redraw();
  };


  /*************
   * Assignments
   ************/

   self.documentAssignments = self.disposedPureComputed(function() {
    if (self.assignmentService) {
      return _.filter(self.assignmentService.assignments(), function(assignment) {
        return assignment.currentState.type !== "completed" &&
          _.some(assignment.targets, function(target) {
            return target.id === self.docModel.docId;
          });
      });
    } else {
      return [];
    }
   }).extend({deferred: true});

  // Dispose
  var baseDispose = self.dispose;
  self.dispose = function() {
    AccordionState.deregister(self.docModel.docId);
    stickyRefresh.dispose();
    isOpenSubs.dispose();
    hub.unsubscribe(toggleEditorSubscription);
    baseDispose();
  };

};
