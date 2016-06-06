LUPAPISTE.RollupStatusButtonModel = function( params ) {
  "use strict";
  var self = this;
  var APPROVED = "ok";
  var REJECTED = "requires_user_action";

  ko.utils.extend (self, new LUPAPISTE.ComponentBaseModel() );

  self.params = params;
  self.status = params.status;
  self.text = params.ltext ? loc( params.ltext ) : params.text;

  self.isApproved = self.disposedComputed( function() {
    return self.status() === APPROVED;
    });

  self.isRejected = self.disposedComputed( function() {
    return self.status() === REJECTED;
  });

  self.statusStyles = self.disposedComputed( function() {
    return _.set( {positive: self.isApproved},
                  params.style || "secondary",
                  !self.isApproved());
  });
};
