// Notice form view with reject/approve/delete support.
// Parameters [optional]:
//  form: Notice form
//  [prefix]: If given, used as a prefix for test ids (e.g., notice-form-message
//  -> prefix-notice-form-message)
LUPAPISTE.NoticeFormModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.noticeFormsService;

  self.form = params.form;
  self.open = service.formOpen( self.form.id );

  var hasAuth = _.partial( service.hasAuth, self.form.id );

  self.showEditor = ko.observable( false );

  self.showRemove = self.disposedComputed( function() {
    return !self.showEditor() && hasAuth( "delete-notice-form" );
  });

  self.showOther = self.disposedComputed( function() {
    return !self.showEditor()
      && ( hasAuth( "approve-notice-form")
           || hasAuth( "reject-notice-form") );
  });

  self.rollupStatus = self.disposedComputed( function() {
    return _.get( {ok: "ok", rejected: "requires_user_action"},
                  self.form.status.state );
  });

  self.formApproved = self.disposedComputed( function() {
    return self.form.status.state === "ok";
  });

  self.formRejected = self.disposedComputed( function() {
    return self.form.status.state === "rejected";
  });

  self.note = self.disposedComputed( function() {
    if( !self.showEditor() && self.formRejected() ) {
      return _.trim( self.form.status.info );
    }
  });

  self.rollupText = sprintf( "notice-form.%s.title", self.form.type );
  self.rollupExtraText = self.disposedComputed( function() {
    return self.form.status.state !== "open"
      ? sprintf( "%s %s",
                 util.finnishDateAndTime( self.form.status.timestamp,
                                        "D.M.YYYY HH:mm"),
                 self.form.status.fullname )
      : "";
  });

  self.customerInfo = self.disposedPureComputed( function() {
    if( self.form.customer ) {
      return _.join( [self.form.customer.name, self.form.customer.email,
                      util.formatPhoneNumber( self.form.customer.phone)], ", ");
    }
  });

  self.payerInfo = self.disposedPureComputed( function() {
    if( self.form.customer ) {
      var payer = self.form.customer.payer;
      return payer.permitPayer
        ? loc( "notice-form.payer.permit-payer")
        : sprintf( "%s, %s %s\n%s\n%s %s",
                   payer.name,
                   util.isValidPersonId( payer.identifier ) ? _.toLower( loc("hetu") ) : loc("y-tunnus"),
                   payer.identifier, payer.street, payer.zip, payer.city);

    }
  });

  self.deleteForm = function() {
    hub.send( "show-dialog", {ltitle: "areyousure",
                              size: "medium",
                              component: "yes-no-dialog",
                              componentParams: {ltext: "notice-form.delete-confirmation",
                                                yesFn: _.wrap( self.form.id,
                                                               service.deleteForm)}});
  };

  self.approveForm = _.wrap( self.form.id, service.approveForm );

  self.editor = ko.observable();

  self.disposedSubscribe( self.showEditor,
                          function( show ) {
                            if( show ) {
                              self.editor( self.form.status.info );
                            }
                          });

  self.rejectForm = function() {
    service.rejectForm( {formId: self.form.id,
                         info: _.trim(self.editor())});
    self.showEditor( false );
  };

  self.closeEditor = function( data, event ) {
    if( event.keyCode === 13 ) {
      self.rejectForm();
    }
    return true;
  };

  self.testId = function ( part, index ) {
    return (params.prefix ? ko.unwrap(params.prefix) + "-" : "")
      + "notice-form-"
      + part
      + (_.isNil( index ) ? "" : "-" + index);
  };

  // Global ESC event
  self.addHubListener( "dialog-close", _.wrap( false, self.showEditor));

};
