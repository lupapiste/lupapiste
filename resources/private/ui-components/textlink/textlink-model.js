// Textual link.
// Parameters [optional]:
// text: Link text. For example: Click [here] to proceed.
// url: Url for the embedded link (here in the above example)
// [icon]: Icon class.

LUPAPISTE.TextlinkModel = function( params ) {
  "use strict";
  var self = this;

  // Matches [link] with link text as the first group.
  var regex = /\[([^\]]+)\]/;

  var text = _.escapeHTML( params.text );

  var link = regex.exec( text )[1];

  self.html = _.replace( text, regex, sprintf( "<a href='%s' target='_blank'>%s</a>",
                                               params.url, link ));

  self.iconCss = _.size( params.icon )
    ? _.reduce( params.icon,
                function( acc, cls ) {
                  return _.set( acc, cls, true );
                }, {})
    : null;
};
