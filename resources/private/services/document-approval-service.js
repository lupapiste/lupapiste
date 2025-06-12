LUPAPISTE.DocumentApprovalService = function() {
  "use strict";

  var self = this;

  var documentApprovals = ko.observable({});

  self.setDocumentApprovalsFromResponse = function( docId, response ) {
    documentApprovals( _.set( documentApprovals(),
                              docId,
                              response.documentApprovals ));
  };

  self.setApplicationApprovalsFromResponse = function( response ) {
    documentApprovals( response.applicationApprovals );
  };

  self.groupApproval = function( docId, groupPath ) {
    var g = _.get( documentApprovals(), [docId, _.join(groupPath, ".")], {} );

    var value = g.override || (!g.changed && g.value);
    var fields = !g.override && g.value ? ["timestamp", "user", "note"] : ["note"];
    return _.merge( _.pick( g, fields ),
                    {
                      value: value,
                      showStatus: !g.override && value
                    }
                  );
  };

  self.documentApproval = function( docId ) {
    var axs = _.get( documentApprovals(), docId);
    var a = _.get( axs, "_root", {});
    var value = a.changed ? null : a.value;
    var fields = value ? ["timestamp", "user", "note"] : ["note"];
    return _.merge( _.pick( a, fields),
                    {
                      value: value,
                      // True if the document or any of its groups is rejected.
                      isSummaryRejected: value === "rejected"
                        || (value !== "approved"
                            && _.some( axs, function( v, k ) {
                              return k !== "_root"
                                && !v.override
                                && !v.changed
                                && v.value === "rejected";
                            }))
                    });
  };

  self.getApproval = function( docId, path ) {
    return _.isEmpty( path )
      ? self.documentApproval( docId )
      : self.groupApproval( docId, path );
  };
};
