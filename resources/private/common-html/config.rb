# Require any additional compass plugins here.

# Set this to the root of your project when deployed:
http_path = "/"
css_dir = "../../public/lp-static/css"
sass_dir = "sass"
images_dir = "img"
javascripts_dir = "js"

# You can select your preferred output style here (can be overridden via the command line):
# output_style = :expanded or :nested or :compact or :compressed

# To enable relative paths to assets via compass helper functions. Uncomment:
# relative_assets = true

# To disable debugging comments that display the original location of your selectors. Uncomment:
# line_comments = false


# If you prefer the indented syntax, you might want to regenerate this
# project again passing --syntax sass, or you can uncomment this:
# preferred_syntax = :sass
# and then run:
# sass-convert -R --from scss --to sass sass scss && rm -rf sass && mv scss sass
sass_options = {:unix_newlines => true}


on_stylesheet_saved do |path|
  base = Pathname.getwd()
  relativepath = Pathname.new(path).relative_path_from(base).to_s

  # http://stackoverflow.com/questions/7173000/slash-and-backslash-in-ruby
  using_windows = !!((RUBY_PLATFORM =~ /(win|w)(32|64)$/) || (RUBY_PLATFORM=~ /mswin|mingw/))
  p = (using_windows ? relativepath.gsub('/', '\\') : relativepath)
  puts "Blessc compiling path: #{p}"
  system("blessc --force " + p) unless path[/\d+$/]
  exit $?.exitstatus
end
