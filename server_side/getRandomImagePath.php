<?php

// Just to enable local testing of the script on server side
if (!isset($_SERVER["HTTP_HOST"])) {
	parse_str($argv[1], $_REQUEST);
}

//$basepath = '/mnt/photo';
$basepath = $_REQUEST['basepath'];

function getRandomElemEntfromArray($ar) {
    mt_srand( (double)microtime() * 1000000 ); // php 4.2+ not needed
    $num = array_rand($ar);
    return $ar[$num];
}

function getRandomSubDir($basedir) {

	$returnedDir = $basedir;
	$directories = glob($basedir. '/*' , GLOB_ONLYDIR);

	//print_r("found subdirs:\n");
	//foreach($directories as $val) {
	//    print $val."\n";
	//}

	if (count($directories)  > 0) {
		$dir = getRandomElemEntfromArray($directories);
  		// print_r("drilling down :".$dir."\n");
		return getRandomSubDir($dir);
	} else {
    	//print_r("no subdir found: stopping at ".$basedir."\n");
		return $returnedDir;
	}
}

function getImageListFromDir($path) {
	$images = array();
	if ( $img_dir = @opendir($path) ) {
		while ( false !== ($img_file = readdir($img_dir)) ) {
			if ( preg_match("/(\.jpg|\.JPG|\.JPEG|\.gif|\.png)$/", $img_file) ) {
				$images[] = $img_file;
			}
		}
		closedir($img_dir);
	}
	return $images;
}


$nbTries = 0;
$img = "";

// Search for an image in basepath subdirectories
while (True) {

	$nbTries++;
	//print_r("NbTries= ".$nbTries."\n");

	$dir = getRandomSubDir($basepath);
  	//print_r("final dir: ".$dir."\n");

	$imgList = getImageListFromDir($dir);

    // directory might be empty: it this case just let the loop retry and pick another one
	if (count($imgList) > 0) {
		// at least one image is available in this dir: pick on reandomly
		$img = getRandomElemEntfromArray($imgList);
		break;
	} 

    // just to avoid a potential infinite loop
	if ($nbTries > 10) {
		break;
	}
}

if ($img != "") {
	$imgname = $dir."/".$img;
	$sizeinfo = getimagesize($imgname);
	$width = $sizeinfo[0];
	$heigth = $sizeinfo[1];
	$exif = exif_read_data($imgname, 0, true);
  	echo $imgname.";".$width.";".$heigth.";".$exif['IFD0']['Orientation'];
}
?>
