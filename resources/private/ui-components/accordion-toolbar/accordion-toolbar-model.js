// docMdodel: DocModel instance
// options: DocModel options
LUPAPISTE.AccordionToolbarModel = function( params ) {
  "use strict";
  var self = this;
  var APPROVED = "approved";
  var APPROVE  = "approve";
  var REJECTED = "rejected";
  var REJECT   = "reject";

  self.docModel = params.docModel;
  var auth = self.docModel.authorizationModel;
  self.isOpen = ko.observable();
  self.isOpen.subscribe( params.openCallback );
  self.isOpen( !params.docModelOptions
            || !params.docModelOptions.accordionCollapsed);

  AccordionState.register( self.docModel.docId, self.isOpen );


  self.info = self.docModel.schema.info;
  var meta = self.docModel.getMeta( params.path );
  self.approval = ko.observable( meta ? meta._approved : null );

  var op = self.info.op;

  self.title = ((op && op.name) || self.info.name) + "._group_label";
  self.toggleAccordion = function() {
    self.isOpen( !self.isOpen());
  }

  // Pen
  // Operation description.
  self.description = ko.observable( op ? op.description : null);
  self.showDescription = op && auth.ok( "update-op-description");
  self.showEditor = ko.observable( false );
  self.toggleEditor = function() {
    self.showEditor( !self.showEditor());
    window.Stickyfill.rebuild();
  }
  self.specialKeys = function( data, event ) {
    // Enter closes bubble
    if( event.keyCode === 13 ) {
      self.showEditor( false );
      window.Stickyfill.rebuild();
    }
    return true;
  }

  self.description.subscribe( function( desc ) {
    ajax.command ("update-op-description", {id: self.docModel.appId,
                                            "op-id": op.id,
                                            desc: desc  })
    .success (function() {
      hub.send("op-description-changed", {apId: self.docModel.appId,
                                          "op-id": op.id,
                                          "op-desc": desc  });
    })
    .call();
  })

  // Star
  // Primary vs. secondary operation.
  self.operationName = op ? op.name : "";
  self.isPrimaryOperation = ko.pureComputed( function() {
    var id = op && op.id;
    return id && id === _.get( self.docModel, ["application", "primaryOperation", "id"] );
  });

  self.showStar = op && auth.ok( "change-primary-operation");
  self.starTitle = ko.pureComputed( function() {
    return self.isPrimaryOperation() ? "operations.primary" : "operations.primary.select";
  });
  self.clickStar = function() {
    ajax.command("change-primary-operation",
                 {id: self.docModel.appId,
                  secondaryOperationId: op.id})
    .success(function() {
      repository.load(self.docModel.appId);
    })
    .call();
    return false;
  }

  self.remove = {};
  // Remove
  if (self.info.removable
    && !self.docModel.isDisabled
    && auth.ok("remove-doc")
    && !self.isPrimaryOperation()) {
    self.remove.fun = self.docModel.removeDocument;
    self.remove.testClass = self.docModel.testId( "delete-schemas."
                                                + self.docModel.schemaName )
  }

  // Approval functionality
  self.isApprovable = Boolean(self.info.approvable);
  self.showStatus = ko.pureComputed( _.partial( self.docModel.isApprovalCurrent,
                                                self.docModel.model,
                                                self.approval ));
  self.isApproved = ko.pureComputed(_.partial(self.docModel.approvalStatus,
                                              self.approval,
                                              APPROVED));
  self.isRejected = ko.pureComputed(_.partial(self.docModel.approvalStatus,
                                              self.approval,
                                              REJECTED));


  self.approveTestId = self.docModel.approvalTestId( [], APPROVE );
  self.rejectTestId = self.docModel.approvalTestId( [], REJECT );

  function showButton( operation, excluder ) {
    return self.isApprovable
        && auth.ok( operation + "-doc")
        && (!excluder() || !self.showStatus());
  }

  self.showReject  = ko.pureComputed(_.partial ( showButton, REJECT, self.isRejected ));
  self.showApprove = ko.pureComputed(_.partial ( showButton, APPROVE, self.isApproved ));

  function changeStatus( flag ) {
    self.docModel.updateApproval( [],
                                  flag,
                                  function( approval ) {
                                    self.approval( approval );
                                  } );
  }

  self.reject  = _.partial( changeStatus, false );
  self.approve = _.partial( changeStatus, true );

  self.details = ko.pureComputed( _.partial( self.docModel.approvalInfo,
                                             self.approval ));

  self.showToolbar = self.showStar
                  || self.showDescription
                  || self.remove.fun
                  || self.showStatus()
                  || self.showReject()
                  || self.showApprove();
}
