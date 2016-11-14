LUPAPISTE.RollupStatusButtonModel = function( params ) {
  "use strict";
  var self = this;
  var APPROVED = "ok";
  var REJECTED = "requires_user_action";

  ko.utils.extend (self, new LUPAPISTE.ComponentBaseModel() );

  self.params = params;
  self.status = params.status;
  self.text = params.ltext ? loc( params.ltext ) : ko.unwrap(params.text);

  self.isApproved = self.disposedPureComputed( function() {
    return self.status() === APPROVED;
  });

  self.isRejected = self.disposedPureComputed( function() {
    return self.status() === REJECTED;
  });

  self.statusStyles = self.disposedPureComputed( function() {
    return _.merge(params.style,
                   {positive: self.isApproved});
  });
};
