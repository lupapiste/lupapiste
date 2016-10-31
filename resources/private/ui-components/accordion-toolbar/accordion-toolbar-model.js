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
  self.isOpen.subscribe( params.openCallback );
  self.isOpen( !params.docModelOptions
            || !params.docModelOptions.accordionCollapsed);

  AccordionState.register( self.docModel.docId, self.isOpen );

  self.info = self.docModel.schema.info;

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

  // identifier field is object with keys docId, schema, key, value. Value is observable (can be edited).
  self.identifierField = self.accordionService && self.accordionService.getIdentifier(self.docModel.docId);

  self.showIdentifierEditors = ko.observable(self.identifierField ? (self.identifierField.value() ? false : true) : false).extend({deferred:true});
  var stickyRefresh = self.showIdentifierEditors.subscribe(function() {
    // refresh accordion sitcky state
    _.delay(window.Stickyfill.rebuild, 0);
  });

  var docData = self.accordionService && self.accordionService.getDocumentData(self.docModel.docId);

  self.accordionText = ko.pureComputed(function() {
    // resolve values from given accordionPaths
    var paths = docData && docData.accordionPaths;
    return docutils.accordionText(paths, _.get(docData, "data"));
  });

  // Required accordion title from operation/schema-info name
  self.titleLoc = ((op && op.name) || self.info.name) + "._group_label";

  // Optional accordion header text.
  // Consists of optional properties: identifier field, operation description, and accordion paths (from schema)
  self.headerDescription = ko.pureComputed(function() {
    // if identifier exists, subscribing to it's "value" observable
    var identifier = self.identifierField && self.identifierField.value();
    var operation  = self.operationDescription();
    var accordionText = self.accordionText();
    return docutils.headerDescription(identifier, operation, accordionText);
  });

  self.toggleAccordion = function() {
    self.isOpen( !self.isOpen());
  };

  // Test ids must contain document name in order to avoid
  // hard to track selector conflicts in Robot tests.
  self.testId = function( id ) {
    return self.docModel.testId( id + "-" + self.docModel.schemaName);
  };

  self.remove = {};
  // Remove
  if (self.auth.ok("remove-doc") && !self.isPrimaryOperation()) {
    self.remove.fun = self.docModel.removeDocument;
    self.remove.testClass = self.docModel.testId( "delete-schemas."
                                                + self.docModel.schemaName );
  }

  // Approval functionality
  self.isApprovable = Boolean(self.info.approvable);
  self.showStatus = self.approvalModel.showStatus;
  self.isApproved = self.approvalModel.isApproved;
  self.isRejected = self.approvalModel.isRejected;
  self.isSummaryRejected = self.approvalModel.isSummaryRejected;
  self.details = self.approvalModel.details;

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

  self.showToolbar = ko.pureComputed(function() {
    return self.remove.fun || self.showStatus() || self.showReject() || self.showApprove() || self.hasOperation();
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



  /*************
   * Assignments
   ************/

   self.documentAssignments = self.disposedPureComputed(function() {
    if (self.assignmentService && features.enabled("assignments")) {
      return _.filter(self.assignmentService.assignments(), function(assignment) { return assignment.target[1] === self.docModel.docId;});
    } else {
      return [];
    }
   });

  // Dispose

  self.dispose = function() {
    AccordionState.deregister(self.docModel.docId);
    stickyRefresh.dispose();
    hub.unsubscribe(toggleEditorSubscription);
  };

};
