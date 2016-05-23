// Approval resolution
// Abundance of alternatives causes some complexity.
// APPROVED: either master approval is the latest action or
//           master has been approved and all the (known) groups
//           are approved, too.
// REJECTED: similar to approved.
// NEUTRAL: if master is neutral or the groups are ambigious.
LUPAPISTE.DocumentApprovalModel = function(docModel) {
  "use strict";

  var self = this;

  var REJECTED = "rejected";
  var NEUTRAL  = "neutral";

  var meta = docModel.getMeta([]);

  self.masterApproval = ko.observable( meta ? meta._approved : null );
  self.groupApprovals = ko.observable({});

  function safeMaster() {
    return docModel.safeApproval( docModel.model, self.masterApproval);
  }

  function laterGroups() {
    var master = safeMaster();
    return _.pickBy(self.groupApprovals(),
                   function( a ) {
                     return a && a.timestamp > master.timestamp;
                   } );
  }

  var lastSent = {};

  self.approval = ko.pureComputed( function() {
    var master = safeMaster();
    var later = laterGroups();
    var result = _.every( later,
                          function( a ) {
                            return a.value === master.value;
                          })
               ? master
               : {value: NEUTRAL};
    if( !_.isEqual(lastSent, result)) {
      lastSent = result;
      // Master (this) has changed, let's notify every group.
      docModel.approvalHubSend( result, []);
    }
    return result;
  });

  // Exclamation icon on the accordion should be visible, if...
  // 1. The master or any "later group" is REJECTED
  // 2. The master is NEUTRAL but any group is REJECTED
  self.isSummaryRejected = ko.pureComputed( function() {
    function groupRejected( groups) {
      return _.find( groups, {"value": REJECTED});
    }
    var master = safeMaster();
    return master.value === REJECTED
        || groupRejected( laterGroups())
        || (master.value === NEUTRAL && groupRejected( self.groupApprovals()) );
  });


  // A group sends its approval to the master (this) when
  // the approval status changes (and also during the initialization).
  docModel.approvalHubSubscribe( function( data ) {
    var g = _.clone( self.groupApprovals() );
    g["path" + data.path.join("-")] = data.approval;
    self.groupApprovals( g );
    // We always respond to the sender regardless whether
    // the update triggers full broadcast. This is done to make sure
    // the group receives the master status during initialization.
    docModel.approvalHubSend( self.approval(), [], data.path );
  });

  ko.utils.extend(self, new LUPAPISTE.ApprovalModel(self));

  self.showStatus = ko.pureComputed(_.partial(docModel.isApprovalCurrent, docModel.model, self.approval));

  self.changeStatus = function(flag) {
    docModel.updateApproval([], flag, self.masterApproval);
  };

  // reset approval when document is updated
  self.modificationHubSub = self.addHubListener(
      {eventType: "update-doc-success", documentId: docModel.docId},
      function() {
        if (self.masterApproval() !== null) {
          self.masterApproval(null);
          window.Stickyfill.rebuild();
        }
      });

  self.parentDispose = _.isFunction(self.dispose) ? self.dispose : _.noop;
  self.dispose = function() {
    self.parentDispose();
    _.each(self, function(property) {
      if (_.isFunction(_.get(property, "dispose"))) {
        property.dispose();
      }
    });
  };
};
