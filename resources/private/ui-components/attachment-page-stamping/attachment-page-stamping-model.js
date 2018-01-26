LUPAPISTE.AttachmentPageStampingModel = function( params ) {
    "use strict";

    var self = this;
    var STAMPED = "stamp.comment";

    ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel( params ));

    // Textual representation of the attachment-page-stamping status.
    // Leimattu 21.11.2017 15:20: Sonja Sibbo
    self.stampingInfo = function( stampingData ) {
        var stamping = ko.unwrap( stampingData );
        var text = null;
        if ( stamping && stamping.isStamped ) {
            text = sprintf("%s %s%s %s %s",
                           loc( [STAMPED] ),
                           moment(stamping.timestamp).format( "D.M.YYYY HH:mm" ),
                           ":",
                           stamping.user.firstName,
                           stamping.user.lastName);
        }
        return text;
    };

    if ( params.attachment ) {
        var attachment = params.attachment;
        var service = lupapisteApp.services.attachmentsService;
        self.isStamped = self.disposedPureComputed( _.wrap( attachment, service.isStamped));
        self.showStatus = self.isStamped();
        self.details = self.disposedComputed( function() {
            return self.stampingInfo( service.stampingData( attachment ));
        });
    }
};